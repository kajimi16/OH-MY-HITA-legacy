package com.stupidtree.hitax.eas.selectable

import com.stupidtree.hitax.data.model.timetable.TermSubject
import org.json.JSONObject

/** 上课时间：移植 HITA_Android 3.1.2 `ShenzhenCourseMeeting`。 */
data class CourseMeeting(
    val weeks: List<Int> = emptyList(),
    val weekday: Int = 0,
    val beginPeriod: Int = 0,
    val endPeriod: Int = 0,
    val teacher: String = "",
    val location: String = "",
)

/** 可选课程：课程本体 + meetings（用于冲突检测）。 */
data class SelectableCourse(
    val subject: TermSubject,
    val meetings: List<CourseMeeting> = emptyList(),
)

/**
 * meetings 解析与冲突判定，逻辑照搬 3.1.2 `ShenzhenCourseCatalogParser`：
 * 优先读结构化 `sksjList`，其次读行内字段，最后从课表文本（如「星期一第3-4节{1-16周}」）解析。
 */
object CourseMeetings {

    fun parse(row: JSONObject, rawSchedule: String, fallbackTeacher: String = ""): List<CourseMeeting> {
        val structured = row.optJSONArray("sksjList") ?: row.optJSONArray("timeList")
        if (structured != null) {
            val list = (0 until structured.length()).mapNotNull { i -> structured.optJSONObject(i) }
                .mapNotNull { parseStructured(it, row, fallbackTeacher) }
            if (list.isNotEmpty()) return list.distinct()
        }
        parseStructured(row, row, fallbackTeacher)?.let { return listOf(it) }
        if (rawSchedule.isBlank()) return emptyList()
        return rawSchedule.split(Regex("[;；\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { fragment ->
                val weekday = parseWeekday(fragment) ?: return@mapNotNull null
                val period = PERIOD_REGEX.find(fragment) ?: return@mapNotNull null
                val begin = period.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val end = period.groupValues[2].toIntOrNull() ?: begin
                val weeks = parseWeeks(fragment)
                if (weeks.isEmpty()) return@mapNotNull null
                val location = fragment.substringAfter("节", "").trim(' ', ',', '，', ';', '；', '[', ']', '【', '】')
                CourseMeeting(weeks, weekday, begin, end, fallbackTeacher, location)
            }
            .distinct()
    }

    private fun parseStructured(row: JSONObject, fallback: JSONObject, fallbackTeacher: String): CourseMeeting? {
        if (row.optString("key").equals("bz", ignoreCase = true)) return null
        val weekday = parseWeekday(firstNonBlank(row, fallback, "xqj", "xq", "weekday")) ?: return null
        val begin = firstNonBlank(row, fallback, "ksjc", "qsjc", "beginPeriod").toDoubleOrNull()?.toInt() ?: return null
        val end = firstNonBlank(row, fallback, "jsjc", "zzjc", "endPeriod").toDoubleOrNull()?.toInt() ?: begin
        val weeks = parseWeeks(firstNonBlank(row, fallback, "zc", "skzc", "weeks"))
        if (weeks.isEmpty()) return null
        return CourseMeeting(
            weeks = weeks,
            weekday = weekday,
            beginPeriod = begin,
            endPeriod = end,
            teacher = firstNonBlank(row, fallback, "skjs", "jsxm", "teacher").ifBlank { fallbackTeacher },
            location = firstNonBlank(row, fallback, "jasmc", "cdmc", "skdd", "location"),
        )
    }

    private fun firstNonBlank(primary: JSONObject, fallback: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { primary.optString(it).takeIf { v -> v.isNotBlank() } }
            ?: keys.firstNotNullOfOrNull { fallback.optString(it).takeIf { v -> v.isNotBlank() } }
            ?: ""

    fun parseWeeks(raw: String): List<Int> {
        val normalized = raw.trim().replace('－', '-').replace('—', '-').replace('~', '-').replace("至", "-")
        if (normalized.length in 33..34 && normalized.all { it == '0' || it == '1' }) {
            return normalized.takeLast(33).mapIndexedNotNull { index, value -> if (value == '1') index + 1 else null }
        }
        val expression = WEEK_REGEX.find(normalized)?.groupValues?.getOrNull(1) ?: normalized
        val parity = when {
            normalized.contains('单') -> 1
            normalized.contains('双') -> 0
            else -> null
        }
        return buildSet {
            expression.split(',', '，', '、').forEach { component ->
                val numbers = NUMBER_REGEX.findAll(component).mapNotNull { it.value.toIntOrNull() }.toList()
                when {
                    numbers.size >= 2 && component.contains('-') -> {
                        val first = numbers[0]
                        val last = numbers[1]
                        if (first <= last) (first..last).filterTo(this) { it in 1..33 && (parity == null || it % 2 == parity) }
                    }
                    numbers.isNotEmpty() -> numbers.filterTo(this) { it in 1..33 && (parity == null || it % 2 == parity) }
                }
            }
        }.sorted()
    }

    fun parseWeekday(raw: String): Int? {
        raw.trim().toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        return when {
            raw.contains("星期一") || raw.contains("周一") -> 1
            raw.contains("星期二") || raw.contains("周二") -> 2
            raw.contains("星期三") || raw.contains("周三") -> 3
            raw.contains("星期四") || raw.contains("周四") -> 4
            raw.contains("星期五") || raw.contains("周五") -> 5
            raw.contains("星期六") || raw.contains("周六") -> 6
            raw.contains("星期日") || raw.contains("星期天") || raw.contains("周日") || raw.contains("周天") -> 7
            else -> null
        }
    }

    fun weekdayName(weekday: Int): String =
        listOf("一", "二", "三", "四", "五", "六", "日").getOrNull(weekday - 1) ?: "?"

    /** 与已选课程求交集；返回可直接展示的冲突描述。 */
    fun conflict(course: SelectableCourse, selected: List<SelectableCourse>): String? {
        selected.forEach { other ->
            course.meetings.forEach { a ->
                other.meetings.forEach { b ->
                    if (a.weekday == b.weekday &&
                        a.beginPeriod <= b.endPeriod && b.beginPeriod <= a.endPeriod
                    ) {
                        val weeks = a.weeks.intersect(b.weeks.toSet()).sorted()
                        if (weeks.isNotEmpty()) {
                            return "与 ${other.subject.name} 冲突：第${weeks.joinToString("、")}周，周${weekdayName(a.weekday)}，" +
                                "第${maxOf(a.beginPeriod, b.beginPeriod)}-${minOf(a.endPeriod, b.endPeriod)}节"
                        }
                    }
                }
            }
        }
        return null
    }

    private val WEEK_REGEX = Regex("(?:第)?([0-9、,，\\-~至单双]+)周")
    private val PERIOD_REGEX = Regex("(?:第)?(\\d{1,2})(?:[-~至](\\d{1,2}))?节")
    private val NUMBER_REGEX = Regex("\\d+")
}
