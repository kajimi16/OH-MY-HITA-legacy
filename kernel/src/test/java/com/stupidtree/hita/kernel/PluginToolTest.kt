package com.stupidtree.hita.kernel

import com.stupidtree.hita.api.Plugin
import com.stupidtree.hita.api.PluginContext
import com.stupidtree.hita.api.PluginToolHandler
import com.stupidtree.hita.api.PluginToolParam
import com.stupidtree.hita.api.PluginToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class TestPlugin(
        override val id: String,
        val body: (PluginContext) -> Unit,
    ) : Plugin {
        override fun install(ctx: PluginContext) = body(ctx)
    }

    private val spec = PluginToolSpec(
        name = "hello_tool",
        description = "say hello",
        parameters = listOf(PluginToolParam("name", required = true)),
    )

    @Test
    fun pluginContributedToolIsVisibleAndRemovedOnUninstall() {
        val host = PluginHost(tmp.newFolder("plugins"))
        var retained: PluginContext? = null

        host.install(TestPlugin("plugin") { ctx ->
            retained = ctx
            ctx.tools.register(spec, PluginToolHandler { "ok" })
        })

        assertEquals(listOf("hello_tool"), host.pluginTools().map { it.spec.name })

        host.uninstall("plugin")
        assertTrue(host.pluginTools().isEmpty())

        // 卸载后保留的 ctx 不能再注册，避免 hook/tool 永久泄漏。
        retained!!.tools.register(spec, PluginToolHandler { "late" })
        assertTrue(host.pluginTools().isEmpty())
    }
}
