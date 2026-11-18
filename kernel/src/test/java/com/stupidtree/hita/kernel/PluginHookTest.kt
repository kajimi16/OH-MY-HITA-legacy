package com.stupidtree.hita.kernel

import com.stupidtree.hita.api.HookKey
import com.stupidtree.hita.api.Plugin
import com.stupidtree.hita.api.PluginContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginHookTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class TestPlugin(
        override val id: String,
        val body: (PluginContext) -> Unit,
    ) : Plugin {
        override fun install(ctx: PluginContext) = body(ctx)
    }

    @Test
    fun hooksRunInRegistrationOrderAndCanDelegate() {
        val host = PluginHost(tmp.newFolder("plugins"))
        val key = HookKey<Int>("counter")

        host.install(TestPlugin("first") { ctx ->
            ctx.hooks.register(key) { value, next -> next(value + 1) }
        })
        host.install(TestPlugin("second") { ctx ->
            ctx.hooks.register(key) { value, next ->
                if (value >= 2) value * 10 else next(value)
            }
        })

        assertEquals(20, host.runHook(key, 1))
    }

    @Test
    fun uninstallRemovesThePluginHook() {
        val host = PluginHost(tmp.newFolder("plugins"))
        val key = HookKey<Int>("counter")

        host.install(TestPlugin("first") { ctx ->
            ctx.hooks.register(key) { value, next -> next(value + 1) }
        })
        host.install(TestPlugin("second") { ctx ->
            ctx.hooks.register(key) { value, next ->
                if (value >= 2) value * 10 else next(value)
            }
        })

        host.uninstall("second")

        assertEquals(2, host.runHook(key, 1))
    }

    @Test
    fun shortCircuitSkipsLaterListeners() {
        val host = PluginHost(tmp.newFolder("plugins"))
        val key = HookKey<String>("text")

        host.install(TestPlugin("first") { ctx ->
            ctx.hooks.register(key) { _, _ -> "stopped" }
        })
        host.install(TestPlugin("second") { ctx ->
            ctx.hooks.register(key) { value, next -> next("$value!") }
        })

        assertEquals("stopped", host.runHook(key, "start"))
    }
}
