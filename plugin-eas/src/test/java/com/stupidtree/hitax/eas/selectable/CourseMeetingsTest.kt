package com.stupidtree.hitax.eas.selectable

import com.stupidtree.hitax.data.model.timetable.TermSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CourseMeetings 的纯逻辑单测：周次/星期解析与冲突判定，不依赖网络与 Android。 */
class CourseMeetingsTest {

    private fun course(name: String, vararg meetings: CourseMeeting) =
        SelectableCourse(TermSubject().apply { this.name = name }, meetings.toList())

    private fun meeting(weeks: List<Int>, weekday: Int, begin: Int, end: Int) =
        CourseMeeting(weeks = weeks, weekday = weekday, beginPeriod = begin, endPeriod = end)

    // ---------------- parseWeeks ----------------

    @Test
    fun parsesWeekRange() {
        assertEquals((1..16).toList(), CourseMeetings.parseWeeks("1-16周"))
    }

    @Test
    fun parsesDiscreteWeeksAndRanges() {
        val weeks = CourseMeetings.parseWeeks("1-8,10-12")
        assertEquals((1..8).toList() + (10..12).toList(), weeks)
    }

    @Test
    fun parsesOddWeeksOnly() {
        val weeks = CourseMeetings.parseWeeks("1-8周(单)")
        assertEquals(listOf(1, 3, 5, 7), weeks)
    }

    @Test
    fun parsesEvenWeeksOnly() {
        val weeks = CourseMeetings.parseWeeks("2-8双周")
        assertEquals(listOf(2, 4, 6, 8), weeks)
    }

    @Test
    fun parsesBitMaskWeeks() {
        // 34 位位串：首位是占位，后 33 位对应 1..33 周
        val mask = "0" + "1".repeat(3) + "0".repeat(30)
        assertEquals(listOf(1, 2, 3), CourseMeetings.parseWeeks(mask))
    }

    @Test
    fun ignoresOutOfRangeWeeks() {
        assertTrue(CourseMeetings.parseWeeks("0,34,40").isEmpty())
    }

    @Test
    fun blankWeekStringYieldsEmpty() {
        assertTrue(CourseMeetings.parseWeeks("").isEmpty())
    }

    // ---------------- parseWeekday ----------------

    @Test
    fun parsesWeekdayFromChineseText() {
        assertEquals(1, CourseMeetings.parseWeekday("星期一第1-2节"))
        assertEquals(7, CourseMeetings.parseWeekday("周日 3-4节"))
        assertEquals(6, CourseMeetings.parseWeekday("星期六"))
    }

    @Test
    fun parsesWeekdayFromNumber() {
        assertEquals(3, CourseMeetings.parseWeekday("3"))
        assertNull(CourseMeetings.parseWeekday("8"))
        assertNull(CourseMeetings.parseWeekday("随便写的"))
    }

    // ---------------- conflict ----------------

    @Test
    fun reportsConflictOnSameWeekdayOverlappingPeriods() {
        val selected = course("数据结构", meeting(listOf(1, 2, 3, 4), 3, 3, 4))
        val candidate = course("操作系统", meeting(listOf(3, 4, 5), 3, 4, 5))

        val conflict = CourseMeetings.conflict(candidate, listOf(selected))

        assertEquals(
            "与 数据结构 冲突：第3、4周，周三，第4-4节",
            conflict,
        )
    }

    @Test
    fun ignoresDifferentWeekday() {
        val selected = course("数据结构", meeting(listOf(1, 2, 3), 3, 3, 4))
        val candidate = course("操作系统", meeting(listOf(1, 2, 3), 4, 3, 4))
        assertNull(CourseMeetings.conflict(candidate, listOf(selected)))
    }

    @Test
    fun ignoresNonOverlappingPeriods() {
        val selected = course("数据结构", meeting(listOf(1, 2, 3), 3, 1, 2))
        val candidate = course("操作系统", meeting(listOf(1, 2, 3), 3, 3, 4))
        assertNull(CourseMeetings.conflict(candidate, listOf(selected)))
    }

    @Test
    fun ignoresWhenWeeksDoNotIntersect() {
        val selected = course("数据结构", meeting(listOf(1, 2, 3), 3, 3, 4))
        val candidate = course("操作系统", meeting(listOf(9, 10), 3, 3, 4))
        assertNull(CourseMeetings.conflict(candidate, listOf(selected)))
    }

    @Test
    fun ignoresCourseWithoutMeetings() {
        val selected = course("数据结构")
        val candidate = course("操作系统", meeting(listOf(1, 2), 3, 3, 4))
        assertNull(CourseMeetings.conflict(candidate, listOf(selected)))
    }

    @Test
    fun noSelectionMeansNoConflict() {
        val candidate = course("操作系统", meeting(listOf(1, 2), 3, 3, 4))
        assertNull(CourseMeetings.conflict(candidate, emptyList()))
    }
}
