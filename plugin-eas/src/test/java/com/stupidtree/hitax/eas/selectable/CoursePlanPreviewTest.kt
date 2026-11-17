package com.stupidtree.hitax.eas.selectable

import com.stupidtree.hitax.data.model.timetable.TermSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePlanPreviewTest {

    private fun course(name: String, vararg meetings: CourseMeeting) =
        SelectableCourse(TermSubject().apply { this.name = name; this.key = name }, meetings.toList())

    private fun meeting(weeks: List<Int>, weekday: Int, begin: Int, end: Int) =
        CourseMeeting(weeks = weeks, weekday = weekday, beginPeriod = begin, endPeriod = end)

    @Test
    fun projectsOnlyCoursesMeetingTargetWeek() {
        val c = course("数据结构", meeting(listOf(1, 2), 3, 3, 4), meeting(listOf(5), 5, 1, 2))
        val week2 = CoursePlanPreview.eventsForWeek(listOf(c), 2, 12)
        assertEquals(1, week2.size)
        assertEquals(3, week2.first().weekday)

        val week5 = CoursePlanPreview.eventsForWeek(listOf(c), 5, 12)
        assertEquals(1, week5.size)
        assertEquals(5, week5.first().weekday)
    }

    @Test
    fun emptyWeekYieldsNothing() {
        val c = course("数据结构", meeting(listOf(1, 2), 3, 3, 4))
        assertTrue(CoursePlanPreview.eventsForWeek(listOf(c), 8, 12).isEmpty())
    }

    @Test
    fun eventsAreOrderedByWeekdayThenPeriod() {
        val a = course("B课", meeting(listOf(1), 5, 1, 2))
        val b = course("A课", meeting(listOf(1), 3, 5, 6))
        val events = CoursePlanPreview.eventsForWeek(listOf(a, b), 1, 12)
        assertEquals(listOf("A课", "B课"), events.map { it.name })
    }

    @Test
    fun maxWeekUsesLargestWeekAcrossCourses() {
        val c = course("数据结构", meeting(listOf(1, 16), 3, 3, 4))
        assertEquals(16, CoursePlanPreview.maxWeek(listOf(c)))
        assertEquals(1, CoursePlanPreview.maxWeek(emptyList()))
    }

    @Test
    fun outOfRangePeriodsGoToIncomplete() {
        val ok = course("正常课", meeting(listOf(1), 3, 1, 2))
        val bad = course("越界课", meeting(listOf(1), 3, 11, 14))
        val incomplete = CoursePlanPreview.incomplete(listOf(ok, bad), 12)
        assertEquals(listOf("越界课"), incomplete.map { it.subject.name })
        assertTrue(CoursePlanPreview.eventsForWeek(listOf(bad), 1, 12).isEmpty())
    }

    @Test
    fun courseWithoutMeetingsIsIncomplete() {
        val c = course("无时间课")
        assertEquals(1, CoursePlanPreview.incomplete(listOf(c), 12).size)
    }

    @Test
    fun eventIdIsStableAcrossCalls() {
        val c = course("数据结构", meeting(listOf(1), 3, 1, 2))
        val first = CoursePlanPreview.eventsForWeek(listOf(c), 1, 12).single().id
        val second = CoursePlanPreview.eventsForWeek(listOf(c), 1, 12).single().id
        assertEquals(first, second)
    }
}
