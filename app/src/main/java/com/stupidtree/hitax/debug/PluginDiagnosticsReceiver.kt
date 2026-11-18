package com.stupidtree.hitax.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stupidtree.hitax.BuildConfig
import com.stupidtree.hitax.HApplication
import com.stupidtree.hitax.market.CompositionKind
import com.stupidtree.hitax.market.CompositionStore
import com.stupidtree.hitax.market.CompositionTarget

/** Debug-only：adb shell am broadcast -a com.stupidtree.hitax.DEBUG_DUMP_PLUGINS */
class PluginDiagnosticsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val app = context.applicationContext as? HApplication ?: return
        when (intent.action) {
            ACTION -> app.dumpPluginDiagnostics()
            ACTION_COMPOSITION -> app.dumpCompositionDiagnostics()
            ACTION_SET_KILL_SWITCH -> {
                val kind = runCatching {
                    CompositionKind.valueOf(
                        intent.getStringExtra(EXTRA_KIND).orEmpty().uppercase()
                    )
                }.getOrNull() ?: return
                val id = intent.getStringExtra(EXTRA_ID)?.trim()?.takeIf { it.isNotBlank() } ?: return
                CompositionStore.setKillSwitch(
                    context,
                    CompositionTarget(kind, id),
                    intent.getBooleanExtra(EXTRA_ENABLED, true),
                )
                app.dumpCompositionDiagnostics()
            }
        }
    }

    companion object {
        const val ACTION = "com.stupidtree.hitax.DEBUG_DUMP_PLUGINS"
        const val ACTION_COMPOSITION = "com.stupidtree.hitax.DEBUG_DUMP_COMPOSITION"
        const val ACTION_SET_KILL_SWITCH = "com.stupidtree.hitax.DEBUG_SET_KILL_SWITCH"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_ENABLED = "enabled"
    }
}
