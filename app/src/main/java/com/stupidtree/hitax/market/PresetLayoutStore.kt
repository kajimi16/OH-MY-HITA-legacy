package com.stupidtree.hitax.market

import android.content.Context
import com.google.gson.Gson

/**
 * 当前生效整合包声明的主界面布局（底栏 tab 列表）。
 *
 * 预设通过 `tabs` 字段描述复刻目标的主界面结构（如 HITA_Android 3.1.2 的 6 个 tab），
 * 启用预设时写入这里；宿主装配主界面时只读这里。没有布局时退回原有的
 * “4 个基页 + 插件 tab 追加”行为，保证非复刻型预设不受影响。
 */
object PresetLayoutStore {
    private const val PREFS = "preset_layout"
    private const val KEY_PRESET = "preset_id"
    private const val KEY_TABS = "tabs_json"

    private val gson = Gson()

    fun save(context: Context, presetId: String, tabs: List<PresetTab>?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (tabs.isNullOrEmpty()) {
            editor.remove(KEY_TABS)
        } else {
            editor.putString(KEY_TABS, gson.toJson(tabs))
        }
        editor.putString(KEY_PRESET, presetId).apply()
    }

    fun presetId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PRESET, null)

    fun tabs(context: Context): List<PresetTab> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TABS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<PresetTab>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList()).filter { it.id.isNotBlank() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}