package com.stupidtree.hitax.data.source.web.eas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenbuClassroomParserTest {

    /** 42 个时段列 + 前面的固定列。 */
    private fun classroomRow(name: String, capacity: String?, occupied: Set<Int> = emptySet()): String = buildString {
        append("<tr><td>").append(name).append("</td>")
        if (capacity != null) append("<td>").append(capacity).append("</td>")
        for (index in 0 until 42) {
            append("<td>")
            if (index in occupied) append("<div class=\"kjs_icon kjs_icon01\"></div>")
            append("</td>")
        }
        append("</tr>")
    }

    private fun classroomPage(vararg rows: String): String = buildString {
        append("<html><body><table class=\"dataTable\">")
        append("<tr><th>教室</th><th>容量</th>")
        for (day in 1..7) append("<th>周").append(day).append("</th>")
        append("</tr>")
        rows.forEach { append(it) }
        append("</table></body></html>")
    }

    @Test
    fun parsesRoomNameCapacityAndSchedule() {
        val html = classroomPage(classroomRow("A101", "60座"))
        val room = BenbuClassroomParser.parseEmptyClassroomHtml(html).single()

        assertEquals("A101", room.name)
        assertEquals("A101", room.id)
        assertEquals(60, room.capacity)
        // 7 天 × 6 段 × 每段 2 节 = 84 条
        assertEquals(84, room.scheduleList.size)
        assertEquals(1, room.scheduleList.first().optInt("XQJ"))
        assertEquals(1, room.scheduleList.first().optInt("XJ"))
        assertEquals("", room.scheduleList.first().optString("PKBJ"))
    }

    @Test
    fun occupiedCellMarksBothPeriodsOfTheBlock() {
        val html = classroomPage(classroomRow("B202", "40", occupied = setOf(0)))
        val room = BenbuClassroomParser.parseEmptyClassroomHtml(html).single()

        val firstTwo = room.scheduleList.take(2)
        assertEquals(listOf(1, 2), firstTwo.map { it.optInt("XJ") })
        assertTrue(firstTwo.all { it.optString("PKBJ") == "占用" })
        assertTrue(room.scheduleList.drop(2).all { it.optString("PKBJ").isEmpty() })
    }

    @Test
    fun dayAndBlockMappingFollowsColumnIndex() {
        val html = classroomPage(classroomRow("C303", "30", occupied = setOf(41)))
        val room = BenbuClassroomParser.parseEmptyClassroomHtml(html).single()

        // 第 41 号格子 = 第 7 天第 6 段 → 11、12 节
        val lastTwo = room.scheduleList.takeLast(2)
        assertEquals(listOf(7, 7), lastTwo.map { it.optInt("XQJ") })
        assertEquals(listOf(11, 12), lastTwo.map { it.optInt("XJ") })
        assertTrue(lastTwo.all { it.optString("PKBJ") == "占用" })
    }

    @Test
    fun rowWithoutCapacityColumnStillParses() {
        val html = classroomPage(classroomRow("D404", null))
        val room = BenbuClassroomParser.parseEmptyClassroomHtml(html).single()
        assertEquals("D404", room.name)
        assertEquals(0, room.capacity)
        assertEquals(84, room.scheduleList.size)
    }

    @Test
    fun rowsWithTooFewCellsAreSkipped() {
        val html = classroomPage("<tr><td>E505</td><td>20</td><td></td><td></td></tr>")
        assertTrue(BenbuClassroomParser.parseEmptyClassroomHtml(html).isEmpty())
    }

    @Test
    fun pageWithoutDataTableYieldsNothing() {
        assertTrue(BenbuClassroomParser.parseEmptyClassroomHtml("<html><body></body></html>").isEmpty())
        assertTrue(BenbuClassroomParser.parseEmptyClassroomHtml("").isEmpty())
    }
}