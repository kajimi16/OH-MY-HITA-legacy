package com.stupidtree.hitax.eas.selectable

import java.util.UUID

/** 周预览事件：移植 3.1.2 `CoursePlanPreviewProjection.eventsForWeek` 的字段与筛选规则。 */
data class PreviewEvent(
    val id: String,
    val name: String,
    val weekday: Int,
    val beginPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val location: String,
    val teacher: String,
)

/**
 * 选课周预览投影：
 * - 只投影 meetings 结构完整且节次落在课表范围内的课程；
 * - 事件 id 用稳定 UUID（课程 + meeting 序号 + 周次），保证同一周重复预览不重复；
 * - 其余课程归入「无法投影」，与上游 incompleteCourses 语义一致。
 */
object CoursePlanPreview {

    fun maxWeek(courses: List<SelectableCourse>): Int =
        courses.flatMap { it.meetings }
            .flatMap { it.weeks }
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1

    fun eventsForWeek(courses: List<SelectableCourse>, week: Int, periodCount: Int): List<PreviewEvent> {
        val targetWeek = week.coerceAtLeast(1)
        return courses.flatMap { course ->
            val courseId = course.subject.key ?: course.subject.id
            course.meetings.mapIndexedNotNull { index, meeting ->
                if (targetWeek !in meeting.weeks ||
                    !isComplete(meeting) ||
                    meeting.beginPeriod !in 1..periodCount ||
                    meeting.endPeriod !in 1..periodCount
                ) {
                    return@mapIndexedNotNull null
                }
                PreviewEvent(
                    id = stableUuid("course-plan-preview:$courseId:$index:$targetWeek"),
                    name = course.subject.name,
                    weekday = meeting.weekday,
                    beginPeriod = meeting.beginPeriod,
                    endPeriod = meeting.endPeriod,
                    weeks = meeting.weeks,
                    location = meeting.location,
                    teacher = meeting.teacher,
                )
            }
        }.sortedWith(compareBy({ it.weekday }, { it.beginPeriod }))
    }

    fun incomplete(courses: List<SelectableCourse>, periodCount: Int): List<SelectableCourse> =
        courses.filter { course ->
            course.meetings.isEmpty() || course.meetings.any { meeting ->
                !isComplete(meeting) ||
                    meeting.beginPeriod !in 1..periodCount ||
                    meeting.endPeriod !in 1..periodCount
            }
        }

    private fun isComplete(meeting: CourseMeeting): Boolean =
        meeting.weeks.isNotEmpty() && meeting.weekday in 1..7 &&
            meeting.beginPeriod > 0 && meeting.endPeriod >= meeting.beginPeriod

    private fun stableUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
}
