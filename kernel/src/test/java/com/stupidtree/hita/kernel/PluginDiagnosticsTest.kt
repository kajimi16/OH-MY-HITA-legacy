package com.stupidtree.hita.kernel

import com.stupidtree.hita.api.Plugin
import com.stupidtree.hita.api.PluginContext
import com.stupidtree.hita.api.ServiceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginDiagnosticsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newHost(): PluginHost = PluginHost(tmp.newFolder("plugins"))

    private class TestPlugin(
        override val id: String,
        val body: (PluginContext) -> Unit = {},
    ) : Plugin {
        override fun install(ctx: PluginContext) = body(ctx)
    }

    @Test
    fun reportsInstalledPluginsAndServiceOwnersInStableOrder() {
        val host = newHost()
        val globalKey = ServiceKey<String>("global.service")
        val pluginKey = ServiceKey<String>("plugin.service")
        host.provideGlobal(globalKey, "host")
        host.install(TestPlugin("b") { ctx -> ctx.provide(pluginKey, "plugin") })
        host.install(TestPlugin("a"))

        val diagnostics = host.diagnostics()

        assertEquals(listOf("a", "b"), diagnostics.installed.map { it.id })
        assertEquals(
            listOf(
                PluginServiceBinding("global.service", PluginHost.HOST_PROVIDER),
                PluginServiceBinding("plugin.service", "b"),
            ),
            diagnostics.services,
        )
    }

    @Test
    fun distinguishesCompatibilityRejectionFromInstallFailure() {
        val host = newHost()
        host.reject("blocked", IllegalStateException("api too new"), PluginFailurePhase.COMPATIBILITY)
        host.install(TestPlugin("broken") { error("boom") })

        val failed = host.diagnostics().failed

        assertEquals(listOf("blocked", "broken"), failed.map { it.id })
        assertEquals(PluginFailurePhase.COMPATIBILITY, failed[0].phase)
        assertEquals(PluginFailurePhase.INSTALL, failed[1].phase)
        assertEquals("boom", failed[1].message)
    }

    @Test
    fun laterProviderIsRejectedAndFirstOwnerSurvivesUninstall() {
        val host = newHost()
        val key = ServiceKey<String>("shared")
        host.install(TestPlugin("first") { ctx -> ctx.provide(key, "first") })
        host.install(TestPlugin("second") { ctx -> ctx.provide(key, "second") })

        assertEquals("first", host.diagnostics().services.single().provider)
        assertEquals(
            listOf(PluginConflict("shared", "first", "second")),
            host.diagnostics().conflicts,
        )

        host.uninstall("second")

        assertEquals("first", host.diagnostics().services.single().provider)
    }

    @Test
    fun successfulInstallClearsAnEarlierFailure() {
        val host = newHost()
        host.reject("same", IllegalStateException("first attempt"), PluginFailurePhase.LOAD)
        host.install(TestPlugin("same"))

        assertTrue(host.diagnostics().failed.isEmpty())
        assertEquals(listOf("same"), host.diagnostics().installed.map { it.id })
    }
}
