package com.stupidtree.hitax.ui.eas.catalog

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 选课草稿条目：只保存展示与标识所需字段，避免把整个课程模型塞进偏好。 */
data class DraftCourse(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val credit: Float = 0f,
)

/**
 * 选课草稿（移植 HITA_Android 3.1.2 `CourseSelectionDraftStore` 的最小形态）：
 * 按「学期」分组保存已选课程，支持增删与清空，重启后仍在。
 *
 * 上游还保存投影开关与冲突结果；冲突检测需要课程 meetings（周次/星期/节次），
 * 当前可选课程接口只解析出 TermSubject，未带 meetings，故冲突提示留待补齐解析后再做。
 */
internal object CourseSelectionDraft {
    private const val PREFS = "course_select_draft"
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, List<DraftCourse>>>() {}.type

    private fun all(context: Context): MutableMap<String, List<DraftCourse>> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("drafts", null) ?: return mutableMapOf()
        return runCatching {
            gson.fromJson<Map<String, List<DraftCourse>>>(raw, type)?.toMutableMap()
        }.getOrNull() ?: mutableMapOf()
    }

    fun courses(context: Context, termKey: String): List<DraftCourse> = all(context)[termKey].orEmpty()

    fun toggle(context: Context, termKey: String, course: DraftCourse): List<DraftCourse> {
        val map = all(context)
        val list = (map[termKey].orEmpty()).toMutableList()
        val existing = list.firstOrNull { it.id == course.id }
        if (existing != null) list.remove(existing) else list += course
        map[termKey] = list
        save(context, map)
        return list
    }

    fun clear(context: Context, termKey: String): List<DraftCourse> {
        val map = all(context)
        map[termKey] = emptyList()
        save(context, map)
        return emptyList()
    }

    private fun save(context: Context, map: Map<String, List<DraftCourse>>) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("drafts", gson.toJson(map)).apply()
    }
}
