package com.stupidtree.hita.kernel

import com.stupidtree.hita.api.HITA_API_VERSION
import com.stupidtree.hita.api.NavEntry
import com.stupidtree.hita.api.NavRegistry
import com.stupidtree.hita.api.Plugin
import com.stupidtree.hita.api.PluginContext
import com.stupidtree.hita.api.SearchTab
import com.stupidtree.hita.api.SearchTabRegistry
import com.stupidtree.hita.api.TabContribution
import com.stupidtree.hita.api.TabRegistry
import com.stupidtree.hita.api.TextNavEntry
import com.stupidtree.hita.api.ViewSearchTab
import com.stupidtree.hita.api.ServiceKey
import com.stupidtree.hita.api.SyncCodec
import com.stupidtree.hita.api.SyncRegistry
import java.io.File

/**
 * 微内核：只负责「装/卸插件」和「持有注册表」。
 *
 * 设计约束：
 * - 不出现任何业务概念（没有课程、没有学期、没有用户）；也不依赖 Android，
 *   因此可以在 JVM 上直接跑单元测试。
 * - 插件的每一次注册都是一条可撤销记录（effect），装载失败或卸载时按相反顺序回滚。
 * - 一个插件装载失败不影响其它插件，失败原因保留在 [failures] 里。
 */
class PluginHost(
    private val pluginsDir: File,
    private val logger: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {

    private val services = LinkedHashMap<String, Any>()
    private val navEntries = LinkedHashMap<String, NavEntry>()
    private val textNavEntries = LinkedHashMap<String, TextNavEntry>()
    private val searchTabEntries = LinkedHashMap<String, SearchTab>()
    private val viewSearchTabEntries = LinkedHashMap<String, ViewSearchTab>()
    private val tabEntries = LinkedHashMap<String, TabContribution>()
    private val syncCodecs = LinkedHashMap<String, SyncCodec>()
    private val installedPlugins = LinkedHashMap<String, Plugin>()
    private val scopes = LinkedHashMap<String, PluginScope>()
    private val failures = LinkedHashMap<String, Throwable>()
    private val failurePhases = LinkedHashMap<String, PluginFailurePhase>()
    private val serviceProviders = LinkedHashMap<String, String>()
    private val descriptors = LinkedHashMap<String, PluginDescriptor>()
    private val pendingPlugins = LinkedHashMap<String, PendingInstall>()
    private val waitingPlugins = LinkedHashMap<String, MutableSet<String>>()
    private val conflicts = ArrayList<PluginConflict>()
    private val globalServices = LinkedHashSet<String>()
    private val availableServices = LinkedHashSet<String>()

    private data class PendingInstall(
        val plugin: Plugin,
        val descriptor: PluginDescriptor,
    )

    /** 宿主只读的导航视图。 */
    val nav: NavRegistry = object : NavRegistry {
        override fun register(entry: NavEntry) {
            navEntries[entry.id] = entry
        }

        override fun entries(): List<NavEntry> = navEntries.values.sortedBy { it.order }

        override fun find(id: String): NavEntry? = navEntries[id]

        override fun registerText(entry: TextNavEntry) {
            textNavEntries[entry.id] = entry
        }

        override fun textEntries(): List<TextNavEntry> =
            textNavEntries.values.sortedBy { it.order }
    }

    /** 宿主只读的搜索 tab 视图。 */
    val searchTabs: SearchTabRegistry = object : SearchTabRegistry {
        override fun register(tab: SearchTab) {
            searchTabEntries[tab.id] = tab
        }

        override fun entries(): List<SearchTab> = searchTabEntries.values.sortedBy { it.order }

        override fun registerView(tab: ViewSearchTab) {
            viewSearchTabEntries[tab.id] = tab
        }

        override fun viewEntries(): List<ViewSearchTab> =
            viewSearchTabEntries.values.sortedBy { it.order }
    }

    /** 宿主只读的主界面 tab 视图。 */
    val tabs: TabRegistry = object : TabRegistry {
        override fun register(tab: TabContribution) {
            tabEntries[tab.id] = tab
        }

        override fun entries(): List<TabContribution> = tabEntries.values.sortedBy { it.order }

        override fun find(id: String): TabContribution? = tabEntries[id]
    }

    /** 宿主只读的同步表视图。 */
    val sync: SyncRegistry = object : SyncRegistry {
        override fun register(codec: SyncCodec) {
            syncCodecs[codec.key] = codec
        }

        override fun get(key: String): SyncCodec? = syncCodecs[key]

        override fun keys(): Set<String> = syncCodecs.keys.toSet()
    }

    fun installAll(plugins: List<Plugin>) {
        plugins.forEach { install(it) }
    }

    fun installAllWithDescriptors(installations: List<PluginInstallation>) {
        installations.forEach { install(it.plugin, it.descriptor) }
    }

    /** 记录一次发生在 `install()` 之前的拒绝，供诊断与测试读取。 */
    fun reject(
        pluginId: String,
        reason: Throwable,
        phase: PluginFailurePhase = PluginFailurePhase.COMPATIBILITY,
    ) {
        failures[pluginId] = reason
        failurePhases[pluginId] = phase
        logger("plugin rejected: $pluginId", reason)
    }

    fun install(plugin: Plugin) {
        install(plugin, PluginDescriptor(plugin.id))
    }

    fun install(plugin: Plugin, descriptor: PluginDescriptor) {
        if (scopes.containsKey(plugin.id) || pendingPlugins.containsKey(plugin.id)) {
            logger("plugin already installed or waiting: ${plugin.id}", null)
            return
        }
        val missing = descriptor.requires.filterNot { it in availableServices }
        if (missing.isNotEmpty()) {
            pendingPlugins[plugin.id] = PendingInstall(plugin, descriptor)
            waitingPlugins[plugin.id] = missing.toMutableSet()
            failures.remove(plugin.id)
            failurePhases.remove(plugin.id)
            logger("plugin waiting for services: ${plugin.id} -> ${missing.joinToString()}", null)
            return
        }
        activate(plugin, descriptor)
        drainPending()
    }

    private fun activate(plugin: Plugin, descriptor: PluginDescriptor) {
        if (plugin.requiresApi > HITA_API_VERSION) {
            reject(
                plugin.id,
                IllegalStateException("requires api ${plugin.requiresApi}, kernel api $HITA_API_VERSION"),
                PluginFailurePhase.COMPATIBILITY,
            )
            return
        }
        val scope = PluginScope(plugin.id)
        scopes[plugin.id] = scope
        installedPlugins[plugin.id] = plugin
        try {
            plugin.install(scope)
            descriptors[plugin.id] = descriptor
            failures.remove(plugin.id)
            failurePhases.remove(plugin.id)
            logger("plugin installed: ${plugin.id}", null)
        } catch (t: Throwable) {
            scope.revokeAll()
            scopes.remove(plugin.id)
            installedPlugins.remove(plugin.id)
            descriptors.remove(plugin.id)
            failures[plugin.id] = t
            failurePhases[plugin.id] = PluginFailurePhase.INSTALL
            logger("plugin install failed: ${plugin.id}", t)
        }
    }

    /** 按“依赖已满足”的波次继续激活等待中的插件。 */
    private fun drainPending() {
        var progressed: Boolean
        do {
            progressed = false
            val iterator = pendingPlugins.entries.iterator()
            while (iterator.hasNext()) {
                val (id, pending) = iterator.next()
                val missing = pending.descriptor.requires.filterNot { it in availableServices }
                if (missing.isEmpty()) {
                    iterator.remove()
                    waitingPlugins.remove(id)
                    activate(pending.plugin, pending.descriptor)
                    progressed = true
                } else {
                    waitingPlugins[id] = missing.toMutableSet()
                }
            }
        } while (progressed)
    }

    fun plugin(pluginId: String): Plugin? = installedPlugins[pluginId]

    fun isInstalled(pluginId: String): Boolean = installedPlugins.containsKey(pluginId)

    /** 插件已提交但仍在等待 requires 中的服务。 */
    fun isWaiting(pluginId: String): Boolean = pendingPlugins.containsKey(pluginId)

    /** [purgeData] 为真时先让插件清理自己的持久化数据，再撤销注册。 */
    fun uninstall(pluginId: String, purgeData: Boolean = false) {
        if (pendingPlugins.remove(pluginId) != null) {
            waitingPlugins.remove(pluginId)
            logger("plugin cancelled while waiting: $pluginId", null)
            return
        }
        val plugin = installedPlugins[pluginId] ?: return
        val scope = scopes[pluginId] ?: return
        if (purgeData) {
            runCatching { plugin.purgeData() }
                .onFailure { logger("plugin purge failed: $pluginId", it) }
        }
        runCatching { plugin.uninstall(scope) }
            .onFailure { logger("plugin uninstall hook failed: $pluginId", it) }
        scope.revokeAll()
        scopes.remove(pluginId)
        installedPlugins.remove(pluginId)
        descriptors.remove(pluginId)
        rebuildAvailableServices()
        drainPending()
    }

    /** 服务可用集只由实际提供者重建，声明了 provides 但被冲突拒绝的插件不算可用。 */
    private fun rebuildAvailableServices() {
        availableServices.clear()
        availableServices.addAll(globalServices)
        serviceProviders.forEach { (service, provider) ->
            if (provider != HOST_PROVIDER && scopes.containsKey(provider)) {
                availableServices.add(service)
            }
        }
    }

    /**
     * 宿主自己提供的服务（不属于任何插件，卸载插件时不会被撤销）。
     * 插件通过 `ctx.service(key)` 读到同一个实例。
     */
    fun <T : Any> provideGlobal(key: ServiceKey<T>, impl: T) {
        services[key.name] = impl
        serviceProviders[key.name] = HOST_PROVIDER
        globalServices.add(key.name)
        availableServices.add(key.name)
        drainPending()
    }

    fun <T : Any> service(key: ServiceKey<T>): T? = services[key.name] as? T

    fun <T : Any> requireService(key: ServiceKey<T>): T =
        services[key.name] as? T
            ?: throw IllegalStateException("service not provided: ${key.name}")

    fun installedIds(): List<String> = installedPlugins.keys.toList()

    fun failures(): Map<String, Throwable> = failures.toMap()

    /** 当前内核状态的稳定快照；字段顺序不依赖 HashMap 遍历顺序。 */
    fun diagnostics(): PluginDiagnostics = PluginDiagnostics(
        installed = installedPlugins.entries
            .map { (id, plugin) -> PluginSnapshot(id, plugin.requiresApi) }
            .sortedBy { it.id },
        failed = failures.entries
            .map { (id, error) ->
                PluginFailure(
                    id = id,
                    phase = failurePhases[id] ?: PluginFailurePhase.INSTALL,
                    message = error.message ?: error::class.java.simpleName,
                )
            }
            .sortedBy { it.id },
        waiting = waitingPlugins.entries
            .map { (id, missing) -> PluginWaiting(id, missing.toList().sorted()) }
            .sortedBy { it.id },
        conflicts = conflicts.toList(),
        degraded = installedPlugins.keys
            .mapNotNull { id ->
                val missing = descriptors[id]?.requires
                    ?.filterNot { it in availableServices }
                    .orEmpty()
                if (missing.isEmpty()) null else PluginDegraded(id, missing.sorted())
            }
            .sortedBy { it.id },
        services = serviceProviders.entries
            .map { (service, provider) -> PluginServiceBinding(service, provider) }
            .sortedBy { it.service },
    )

    /**
     * 单个插件的注册作用域。所有 undo 都是幂等的移除/恢复动作，
     * 因此「安装到一半失败」也能安全回滚。
     */
    inner class PluginScope(private val pluginId: String) : PluginContext {

        private val undoStack = ArrayList<() -> Unit>()

        override fun <T : Any> service(key: ServiceKey<T>): T? = this@PluginHost.service(key)

        override fun <T : Any> provide(key: ServiceKey<T>, impl: T) {
            val existingProvider = serviceProviders[key.name]
            if (existingProvider != null && existingProvider != pluginId) {
                conflicts += PluginConflict(key.name, existingProvider, pluginId)
                logger(
                    "service conflict: ${key.name} kept by $existingProvider, rejected $pluginId",
                    null,
                )
                return
            }
            val previous = services.put(key.name, impl)
            val previousProvider = serviceProviders.put(key.name, pluginId)
            availableServices.add(key.name)
            undoStack += {
                // 宿主或其它插件后来接管该 key 时，卸载不能把它们的实现覆盖掉。
                if (serviceProviders[key.name] == pluginId) {
                    if (previous == null) {
                        services.remove(key.name)
                        serviceProviders.remove(key.name)
                        availableServices.remove(key.name)
                    } else {
                        services[key.name] = previous
                        if (previousProvider == null) serviceProviders.remove(key.name)
                        else serviceProviders[key.name] = previousProvider
                        availableServices.add(key.name)
                    }
                }
            }
        }

        override fun filesDir(): File =
            File(pluginsDir, pluginId).apply { mkdirs() }

        override val nav: NavRegistry = object : NavRegistry {
            override fun register(entry: NavEntry) {
                val previous = navEntries.put(entry.id, entry)
                undoStack += {
                    if (previous == null) navEntries.remove(entry.id) else navEntries[entry.id] = previous
                }
            }

            override fun entries(): List<NavEntry> = this@PluginHost.nav.entries()

            override fun find(id: String): NavEntry? = this@PluginHost.nav.find(id)

            override fun registerText(entry: TextNavEntry) {
                val previous = textNavEntries.put(entry.id, entry)
                undoStack += {
                    if (previous == null) textNavEntries.remove(entry.id)
                    else textNavEntries[entry.id] = previous
                }
            }

            override fun textEntries(): List<TextNavEntry> =
                this@PluginHost.nav.textEntries()
        }

        override val searchTabs: SearchTabRegistry = object : SearchTabRegistry {
            override fun register(tab: SearchTab) {
                val previous = searchTabEntries.put(tab.id, tab)
                undoStack += {
                    if (previous == null) searchTabEntries.remove(tab.id)
                    else searchTabEntries[tab.id] = previous
                }
            }

            override fun entries(): List<SearchTab> = this@PluginHost.searchTabs.entries()

            override fun registerView(tab: ViewSearchTab) {
                val previous = viewSearchTabEntries.put(tab.id, tab)
                undoStack += {
                    if (previous == null) viewSearchTabEntries.remove(tab.id)
                    else viewSearchTabEntries[tab.id] = previous
                }
            }

            override fun viewEntries(): List<ViewSearchTab> =
                this@PluginHost.searchTabs.viewEntries()
        }

        override val tabs: TabRegistry = object : TabRegistry {
            override fun register(tab: TabContribution) {
                val previous = tabEntries.put(tab.id, tab)
                undoStack += {
                    if (previous == null) tabEntries.remove(tab.id) else tabEntries[tab.id] = previous
                }
            }

            override fun entries(): List<TabContribution> = this@PluginHost.tabs.entries()

            override fun find(id: String): TabContribution? = this@PluginHost.tabs.find(id)
        }

        override val sync: SyncRegistry = object : SyncRegistry {
            override fun register(codec: SyncCodec) {
                val previous = syncCodecs.put(codec.key, codec)
                undoStack += {
                    if (previous == null) syncCodecs.remove(codec.key)
                    else syncCodecs[codec.key] = previous
                }
            }

            override fun get(key: String): SyncCodec? = this@PluginHost.sync.get(key)

            override fun keys(): Set<String> = this@PluginHost.sync.keys()
        }

        fun revokeAll() {
            undoStack.asReversed().forEach { runCatching(it) }
            undoStack.clear()
        }
    }

    companion object {
        /** 诊断里表示服务由宿主 `provideGlobal` 提供。 */
        const val HOST_PROVIDER = "host"
    }
}
