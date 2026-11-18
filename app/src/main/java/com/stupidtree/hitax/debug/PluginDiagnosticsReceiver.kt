package com.stupidtree.hitax.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stupidtree.hitax.BuildConfig
import com.stupidtree.hitax.HApplication

/** Debug-only：adb shell am broadcast -a com.stupidtree.hitax.DEBUG_DUMP_PLUGINS */
class PluginDiagnosticsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || intent.action != ACTION) return
        val app = context.applicationContext as? HApplication ?: return
        app.dumpPluginDiagnostics()
    }

    companion object {
        const val ACTION = "com.stupidtree.hitax.DEBUG_DUMP_PLUGINS"
    }
}
