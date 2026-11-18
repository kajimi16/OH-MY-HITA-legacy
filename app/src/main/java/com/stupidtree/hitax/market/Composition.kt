package com.stupidtree.hitax.market

/** 组合条目的种类；id 只在同一种类内唯一。 */
enum class CompositionKind { BUILTIN, DEX, UI }

/** 最终值由哪一层写入；后者覆盖前者。 */
enum class CompositionSource { BASE, PRESET, USER, SAFETY }

data class CompositionEntry(
    val id: String,
    val kind: CompositionKind,
    val enabled: Boolean,
    val required: Boolean,
    val source: CompositionSource,
)

sealed class CompositionMissingReason {
    data object NotInBase : CompositionMissingReason()
    data object NotInstalled : CompositionMissingReason()
    data object NotInIndex : CompositionMissingReason()
}

data class CompositionMissing(
    val id: String,
    val kind: CompositionKind,
    val reason: CompositionMissingReason,
)

/**
 * 一次组合计算的目标状态。
 *
 * 它是纯数据：不包含下载、安装或启用动作；materializer / reconciler 再决定怎么做。
 */
data class CompositionPlan(
    val entries: List<CompositionEntry>,
    val missing: List<CompositionMissing>,
) {
    /**
     * 会阻止应用 preset 的必选缺失。
     *
     * `NotInstalled` 是可安装缺口，必须交给 materializer；只有“索引/清单里根本没有”
     * 才是预检阶段无法解决的阻塞。
     */
    fun blockingRequiredMissing(): List<CompositionMissing> = missing.filter { item ->
        item.reason != CompositionMissingReason.NotInstalled &&
            entries.any { it.id == item.id && it.kind == item.kind && it.required }
    }

    /**
     * `applyPreset` 专属子计划：纯 BASE 条目只用于展示/依赖判断，不能因为应用 preset
     * 被重新启用。PRESET / USER / SAFETY 层仍参与对账。
     */
    fun forPresetApplication(): CompositionPlan =
        if (entries.none { it.source == CompositionSource.BASE }) {
            this
        } else {
            copy(entries = entries.filter { it.source != CompositionSource.BASE })
        }

    fun dump(): String = buildString {
        appendLine("entries:")
        entries.forEach {
            appendLine(
                "  ${it.kind.name.lowercase()} ${it.id} enabled=${it.enabled} " +
                    "source=${it.source.name.lowercase()} required=${it.required}",
            )
        }
        appendLine("missing:")
        missing.forEach {
            appendLine("  ${it.kind.name.lowercase()} ${it.id} reason=${it.reason::class.simpleName}")
        }
    }.trimEnd()
}

/**
 * 只做“组合应该是什么”的计算，不读写磁盘、不访问 Android。
 *
 * 输入是当前事实（base / 已安装 / 索引可用 / 用户禁用 / kill switch）与前后两个 preset。
 * 输出覆盖 base、preset 引用以及“旧 preset 独有条目”，未提到的已安装 UI 插件保持原状。
 */
object CompositionResolver {

    fun resolve(
        baseIds: Set<String>,
        next: MarketPreset?,
        previous: MarketPreset?,
        available: Map<CompositionKind, Set<String>>,
        installed: Map<CompositionKind, Set<String>>,
        userDisabled: Map<CompositionKind, Set<String>> = emptyMap(),
        killSwitches: Set<String> = emptySet(),
    ): CompositionPlan {
        val entries = LinkedHashMap<Pair<CompositionKind, String>, CompositionEntry>()

        baseIds.forEach { id ->
            entries[CompositionKind.BUILTIN to id] = CompositionEntry(
                id = id,
                kind = CompositionKind.BUILTIN,
                enabled = true,
                required = false,
                source = CompositionSource.BASE,
            )
        }

        val nextRefs = next?.refs().orEmpty().distinctBy { it.compositionKey() }
        val nextKeys = nextRefs.associateBy { it.compositionKey() }
        previous?.refs().orEmpty()
            .filterNot { it.compositionKey() in nextKeys }
            // 旧组合只停用 UI/内置；dex 插件的数据与权限更重，保留给用户显式处理。
            .filter { it.kind() != CompositionKind.DEX }
            .forEach { ref ->
                val key = ref.compositionKey()
                entries[key] = CompositionEntry(
                    id = ref.id,
                    kind = key.first,
                    enabled = false,
                    required = false,
                    source = CompositionSource.PRESET,
                )
            }

        nextRefs.forEach { ref ->
            val key = ref.compositionKey()
            entries[key] = CompositionEntry(
                id = ref.id,
                kind = key.first,
                enabled = true,
                required = ref.isRequired(),
                source = CompositionSource.PRESET,
            )
        }

        val missing = mutableListOf<CompositionMissing>()
        nextRefs.forEach { ref ->
            val kind = ref.kind()
            val installedNow = kind != CompositionKind.BUILTIN && ref.id in installed[kind].orEmpty()
            val known = when (kind) {
                CompositionKind.BUILTIN -> ref.id in baseIds
                // 已安装但暂时不在索引里的插件仍可启用，不能误报为缺失。
                CompositionKind.DEX, CompositionKind.UI -> installedNow || ref.id in available[kind].orEmpty()
            }
            if (!known) {
                missing += CompositionMissing(
                    ref.id,
                    kind,
                    if (kind == CompositionKind.BUILTIN) {
                        CompositionMissingReason.NotInBase
                    } else {
                        CompositionMissingReason.NotInIndex
                    },
                )
            } else if (kind != CompositionKind.BUILTIN && !installedNow) {
                missing += CompositionMissing(ref.id, kind, CompositionMissingReason.NotInstalled)
            }
        }

        val resolved = entries.values.map { entry ->
            when {
                entry.id in killSwitches -> entry.copy(
                    enabled = false,
                    source = CompositionSource.SAFETY,
                )
                entry.id in userDisabled[entry.kind].orEmpty() -> entry.copy(
                    enabled = false,
                    source = CompositionSource.USER,
                )
                else -> entry
            }
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.id }))

        return CompositionPlan(
            entries = resolved,
            missing = missing.sortedWith(compareBy({ it.kind.ordinal }, { it.id })),
        )
    }

    private fun PresetPluginRef.compositionKey(): Pair<CompositionKind, String> = kind() to id

    private fun PresetPluginRef.kind(): CompositionKind = when (normalizedKind()) {
        "dex" -> CompositionKind.DEX
        "builtin" -> CompositionKind.BUILTIN
        else -> CompositionKind.UI
    }
}

/** 当前实际状态，供 reconciler 与 plan 做 diff。 */
data class CompositionState(
    val installed: Map<CompositionKind, Set<String>> = emptyMap(),
    val enabled: Map<CompositionKind, Set<String>> = emptyMap(),
)

sealed class CompositionAction {
    data class Install(
        val id: String,
        val kind: CompositionKind,
        val required: Boolean,
    ) : CompositionAction()

    data class Enable(
        val id: String,
        val kind: CompositionKind,
        val required: Boolean,
    ) : CompositionAction()

    data class Disable(
        val id: String,
        val kind: CompositionKind,
        val required: Boolean,
    ) : CompositionAction()

    data class Blocked(
        val missing: CompositionMissing,
        val required: Boolean,
    ) : CompositionAction()
}

/** 执行副作用的最小接口；由 Android 侧适配到 installer / UiPluginManager / BuiltinPluginManager。 */
interface CompositionExecutor {
    /** 返回是否需要重建界面。 */
    fun install(kind: CompositionKind, id: String): Result<Boolean>

    fun enable(kind: CompositionKind, id: String): Result<Boolean>

    fun disable(kind: CompositionKind, id: String): Result<Boolean>
}

data class CompositionReport(
    val installed: Int,
    val enabled: Int,
    val disabled: Int,
    val skipped: Int,
    val failures: List<String>,
    val optionalFailures: List<String>,
    val reloadNeeded: Boolean,
) {
    val allFailures: List<String> get() = failures + optionalFailures
    val canActivate: Boolean get() = failures.isEmpty()
}

object CompositionReconciler {

    /**
     * 先安装/启用目标条目，再停用旧 preset 独有条目；后者只在必选步骤全部成功后执行。
     */
    fun actions(plan: CompositionPlan, state: CompositionState): List<CompositionAction> {
        val missingByKey = plan.missing.associateBy { it.kind to it.id }
        val activations = mutableListOf<CompositionAction>()
        val disables = mutableListOf<CompositionAction>()

        plan.entries.forEach { entry ->
            val key = entry.kind to entry.id
            val missing = missingByKey[key]
            if (!entry.enabled) {
                // 旧 preset 独有条目即使已从索引消失，也要按实际安装状态停用。
                if (entry.id in state.enabled[entry.kind].orEmpty()) {
                    disables += CompositionAction.Disable(entry.id, entry.kind, entry.required)
                }
                return@forEach
            }
            if (missing != null && missing.reason != CompositionMissingReason.NotInstalled) {
                activations += CompositionAction.Blocked(missing, entry.required)
                return@forEach
            }
            val installed = entry.id in state.installed[entry.kind].orEmpty()
            val enabled = entry.id in state.enabled[entry.kind].orEmpty()
            when {
                missing != null -> activations += CompositionAction.Install(
                    entry.id,
                    entry.kind,
                    entry.required,
                )
                !installed -> activations += CompositionAction.Install(
                    entry.id,
                    entry.kind,
                    entry.required,
                )
                !enabled -> activations += CompositionAction.Enable(
                    entry.id,
                    entry.kind,
                    entry.required,
                )
            }
        }
        return activations + disables
    }

    fun execute(
        plan: CompositionPlan,
        state: CompositionState,
        executor: CompositionExecutor,
    ): CompositionReport {
        var installed = 0
        var enabled = 0
        var disabled = 0
        var reload = false
        val failures = mutableListOf<String>()
        val optionalFailures = mutableListOf<String>()

        fun record(required: Boolean, text: String) {
            if (required) failures += text else optionalFailures += text
        }

        val actions = actions(plan, state)
        actions.forEach { action ->
            // 必选步骤失败后不再继续改动组合，避免留下“新组合装一半、旧组合还在”的状态。
            if (failures.isNotEmpty()) return@forEach
            when (action) {
                is CompositionAction.Blocked -> record(
                    action.required,
                    "${action.missing.id}（${action.missing.reason.label()}）",
                )
                is CompositionAction.Install -> executor.install(action.kind, action.id)
                    .fold(
                        onSuccess = {
                            installed++
                            if (it) reload = true
                        },
                        onFailure = { record(action.required, "${action.id}：${it.message ?: "安装失败"}") },
                    )
                is CompositionAction.Enable -> executor.enable(action.kind, action.id)
                    .fold(
                        onSuccess = {
                            enabled++
                            if (it) reload = true
                        },
                        onFailure = { record(action.required, "${action.id}：${it.message ?: "启用失败"}") },
                    )
                is CompositionAction.Disable -> {
                    if (failures.isNotEmpty()) return@forEach
                    executor.disable(action.kind, action.id)
                        .onSuccess {
                            disabled++
                            if (it) reload = true
                        }
                        .onFailure { optionalFailures += "${action.id}：${it.message ?: "停用失败"}" }
                }
            }
        }

        val actionableKeys = actions.mapNotNull { action ->
            when (action) {
                is CompositionAction.Install -> action.kind to action.id
                is CompositionAction.Enable -> action.kind to action.id
                is CompositionAction.Disable -> action.kind to action.id
                is CompositionAction.Blocked -> action.missing.kind to action.missing.id
            }
        }.toSet()
        val skipped = plan.entries.count { (it.kind to it.id) !in actionableKeys }

        return CompositionReport(
            installed = installed,
            enabled = enabled,
            disabled = disabled,
            skipped = skipped,
            failures = failures,
            optionalFailures = optionalFailures,
            reloadNeeded = reload,
        )
    }

    private fun CompositionMissingReason.label(): String = when (this) {
        CompositionMissingReason.NotInBase -> "内置缺失"
        CompositionMissingReason.NotInstalled -> "未安装"
        CompositionMissingReason.NotInIndex -> "索引缺失"
    }
}
