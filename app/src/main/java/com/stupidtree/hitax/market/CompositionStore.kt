package com.stupidtree.hitax.market

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File

/**
 * 组合意图与诊断快照。
 *
 * 只保存宿主安全开关和最近一次 plan/state 的 dump；插件的实际安装/启用状态仍由
 * PluginInstaller / UiPluginManager / BuiltinPluginManager 持有。
 */
object CompositionStore {
    private const val PREFS = "composition_state"
    private const val KEY_KILL_SWITCHES = "kill_switches"
    private const val KEY_APPLIED_PRESET = "applied_preset"
    private const val DIRECTORY = "diagnostics"
    private const val SNAPSHOT = "composition.json"

    fun killSwitches(context: Context): Set<CompositionTarget> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_KILL_SWITCHES, emptySet())
            .orEmpty()
            .mapNotNull(::parseTarget)
            .toSet()

    fun isKillSwitched(context: Context, kind: CompositionKind, id: String): Boolean =
        CompositionTarget(kind, id) in killSwitches(context)

    fun setKillSwitch(context: Context, target: CompositionTarget, enabled: Boolean) {
        val next = killSwitches(context).toMutableSet()
        if (enabled) next.add(target) else next.remove(target)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_KILL_SWITCHES, next.map(::encodeTarget).toSet())
            .apply()
    }

    fun savedPreset(context: Context): MarketPreset? = runCatching {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APPLIED_PRESET, null) ?: return null
        Gson().fromJson(json, MarketPreset::class.java)
    }.getOrNull()

    fun savePreset(context: Context, preset: MarketPreset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPLIED_PRESET, Gson().toJson(preset))
            .apply()
    }

    fun record(context: Context, plan: CompositionPlan, state: CompositionState) {
        write(context, snapshotJson(plan, state, report = null))
    }

    fun recordResult(
        context: Context,
        plan: CompositionPlan,
        state: CompositionState,
        report: CompositionReport,
    ) {
        write(context, snapshotJson(plan, state, report))
    }

    internal fun snapshotJson(
        plan: CompositionPlan,
        state: CompositionState,
        report: CompositionReport? = null,
    ): String = snapshot(plan, state, report).toString()

    private fun snapshot(
        plan: CompositionPlan,
        state: CompositionState,
        report: CompositionReport?,
    ): JsonObject {
        val gson = Gson()
        return JsonObject().apply {
            add("desired", gson.toJsonTree(plan.entries))
            add(
                "missing",
                gson.toJsonTree(
                    plan.missing.map {
                        mapOf(
                            "id" to it.id,
                            "kind" to it.kind.name,
                            "reason" to it.reason::class.simpleName,
                        )
                    }
                ),
            )
            add("actual", gson.toJsonTree(state))
            if (report != null) add("result", gson.toJsonTree(report))
        }
    }

    private fun write(context: Context, json: String) {
        val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        File(dir, SNAPSHOT).writeText(json)
    }

    fun read(context: Context): String? =
        File(File(context.filesDir, DIRECTORY), SNAPSHOT)
            .takeIf { it.isFile }
            ?.readText()

    private fun encodeTarget(target: CompositionTarget): String =
        "${target.kind.name}:${target.id}"

    private fun parseTarget(raw: String): CompositionTarget? = runCatching {
        val separator = raw.indexOf(':')
        if (separator <= 0) return@runCatching null
        CompositionTarget(
            CompositionKind.valueOf(raw.substring(0, separator)),
            raw.substring(separator + 1),
        )
    }.getOrNull()
}
