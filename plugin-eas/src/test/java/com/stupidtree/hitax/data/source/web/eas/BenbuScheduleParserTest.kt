package com.stupidtree.hitax.data.source.web.eas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenbuScheduleParserTest {

    /** 拼一个最小可解析的课表页：表头 + 若干数据行。 */
    private fun schedulePage(vararg rows: String): String = buildString {
        append("<html><body><table class=\"addlist_01\">")
        append("<tr><th>时间</th><th>节次</th>")
        for (day in 1..7) append("<th>星期$day</th>")
        append("</tr>")
        rows.forEach { append(it) }
        append("</table></body></html>")
    }

    private fun row(periodInfo: String, vararg cells: String): String = buildString {
        append("<tr><td>上午</td><td>").append(periodInfo).append("</td>")
        cells.forEach { append("<td>").append(it).append("</td>") }
        append("</tr>")
    }

    @Test
    fun parsesSingleCourseWithRangeWeeks() {
        val html = schedulePage(
            row("第1,2节", "高等数学<br>张三[1-8]周A101", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        val courses = BenbuScheduleParser.parseScheduleHtml(html)

        assertEquals(1, courses.size)
        val course = courses.single()
        assertEquals("高等数学", course.name)
        assertEquals("张三", course.teacher)
        assertEquals("A101", course.classroom)
        assertEquals(1, course.dow)
        assertEquals(1, course.begin)
        assertEquals(2, course.last)
        assertEquals((1..8).toList(), course.weeks)
    }

    @Test
    fun dayColumnMapsToDowByColumnIndexMinusOne() {
        val html = schedulePage(
            row(
                "第1,2节",
                "周一课<br>张三[1-2]周A1",
                "周二课<br>张三[1-2]周A2",
                "周三课<br>张三[1-2]周A3",
                "周四课<br>张三[1-2]周A4",
                "周五课<br>张三[1-2]周A5",
                "周六课<br>张三[1-2]周A6",
                "周日课<br>张三[1-2]周A7",
            ),
        )
        val courses = BenbuScheduleParser.parseScheduleHtml(html)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), courses.map { it.dow })
        assertEquals(
            listOf("周一课", "周二课", "周三课", "周四课", "周五课", "周六课", "周日课"),
            courses.map { it.name },
        )
    }

    @Test
    fun periodRangeBecomesBeginAndCount() {
        val courses = BenbuScheduleParser.parseScheduleHtml(
            schedulePage(row("第5-6节", "英语<br>李四[1-4]周B1", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;")),
        )
        val course = courses.single()
        assertEquals(5, course.begin)
        // 上游 last 存的是节数而不是结束节次
        assertEquals(2, course.last)
    }

    @Test
    fun singlePeriodStaysSinglePeriod() {
        val courses = BenbuScheduleParser.parseScheduleHtml(
            schedulePage(row("第3节", "体育<br>王五[1-4]周操场", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;")),
        )
        val course = courses.single()
        assertEquals(3, course.begin)
        assertEquals(1, course.last)
    }

    @Test
    fun missingPeriodFallsBackToRowIndex() {
        val html = schedulePage(
            row("", "&nbsp;", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
            row("", "自习<br>张三[1-2]周A1", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        val course = BenbuScheduleParser.parseScheduleHtml(html).single()
        assertEquals(2, course.begin)
        assertEquals(2, course.last)
    }

    @Test
    fun oddEvenEntriesWithDifferentRoomsProduceTwoCourses() {
        val html = schedulePage(
            row(
                "第1,2节",
                "线性代数<br>王五[1-8]单周C301，赵六[9-16]双周C302",
                "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val courses = BenbuScheduleParser.parseScheduleHtml(html).sortedBy { it.weeks.first() }

        assertEquals(2, courses.size)
        assertEquals("王五", courses[0].teacher)
        assertEquals("C301", courses[0].classroom)
        assertEquals(listOf(1, 3, 5, 7), courses[0].weeks)
        assertEquals("赵六", courses[1].teacher)
        assertEquals("C302", courses[1].classroom)
        assertEquals(listOf(10, 12, 14, 16), courses[1].weeks)
        assertTrue(courses.all { it.name == "线性代数" })
    }

    @Test
    fun locationOnNextLineIsJoinedToTeacherLine() {
        val html = schedulePage(
            row("第1,2节", "大学物理<br>李四[1-16]周<br>B202", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        val course = BenbuScheduleParser.parseScheduleHtml(html).single()
        assertEquals("李四", course.teacher)
        assertEquals("B202", course.classroom)
        assertEquals((1..16).toList(), course.weeks)
    }

    @Test
    fun bracketWeeksWithoutTeacherKeepTeacherNull() {
        val html = schedulePage(
            row("第1,2节", "形势与政策<br>[1,3,5]周E101", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        val course = BenbuScheduleParser.parseScheduleHtml(html).single()
        assertNull(course.teacher)
        assertEquals("E101", course.classroom)
        assertEquals(listOf(1, 3, 5), course.weeks)
    }

    @Test
    fun courseTypePrefixAndMultiTeacherAreCleaned() {
        val html = schedulePage(
            row(
                "第1,2节",
                "数据结构<br>【必修】钱七/孙八[1-8]周D401",
                "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val course = BenbuScheduleParser.parseScheduleHtml(html).single()
        assertEquals("钱七", course.teacher)
        assertEquals("D401", course.classroom)
    }

    @Test
    fun repeatedCourseNameInTeacherFieldIsRemoved() {
        val html = schedulePage(
            row("第1,2节", "高等数学<br>高等数学张三/李四[1-8]周A101", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        assertEquals("张三", BenbuScheduleParser.parseScheduleHtml(html).single().teacher)
    }

    @Test
    fun missingRoomIsBackfilledWhenTheOtherPartHasOnlyOne() {
        val html = schedulePage(
            row(
                "第1,2节",
                "概率论<br>张三[1-8]单周A101，李四[9-16]双周",
                "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val courses = BenbuScheduleParser.parseScheduleHtml(html).sortedBy { it.weeks.first() }
        assertEquals(2, courses.size)
        assertEquals("A101", courses[0].classroom)
        assertEquals("A101", courses[1].classroom)
        assertEquals("李四", courses[1].teacher)
    }

    @Test
    fun multipleRoomsAreNotBackfilled() {
        val html = schedulePage(
            row(
                "第1,2节",
                "概率论<br>张三[1-8]单周A101，李四[9-16]双周B202，王五[17-18]周",
                "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val courses = BenbuScheduleParser.parseScheduleHtml(html).sortedBy { it.weeks.first() }
        assertEquals(3, courses.size)
        assertNull(courses[2].classroom)
    }

    @Test
    fun hyperlinkTextIsNotDuplicated() {
        val html = schedulePage(
            row(
                "第1,2节",
                "<a href=\"/course/1\">大学英语</a>大学英语<br>张三[1-4]周A201",
                "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val course = BenbuScheduleParser.parseScheduleHtml(html).single()
        assertEquals("大学英语", course.name)
    }

    @Test
    fun pageWithoutScheduleTableYieldsNothing() {
        assertTrue(BenbuScheduleParser.parseScheduleHtml("<html><body><table class=\"other\"></table></body></html>").isEmpty())
        assertTrue(BenbuScheduleParser.parseScheduleHtml("").isEmpty())
    }

    @Test
    fun blankAndTinyCellsAreSkipped() {
        val html = schedulePage(
            row("第1,2节", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        assertTrue(BenbuScheduleParser.parseScheduleHtml(html).isEmpty())
    }

    @Test
    fun lineWithoutWeekSuffixIsDroppedLikeUpstream() {
        // 上游要求教师行带 "[…]周" 标记，否则整段被丢弃；这里是已知行为，不是新 bug
        val html = schedulePage(
            row("第1,2节", "晚课<br>张三[1-8]A101", "&nbsp;", "", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;"),
        )
        assertTrue(BenbuScheduleParser.parseScheduleHtml(html).isEmpty())
    }

    @Test
    fun weeksStringParsingCoversRangesListsAndParity() {
        assertEquals((1..8).toList(), BenbuScheduleParser.parseWeeksString("1-8"))
        assertEquals(listOf(1, 3, 5), BenbuScheduleParser.parseWeeksString("1,3,5"))
        assertEquals(listOf(1, 3, 5, 7), BenbuScheduleParser.parseWeeksString("1-8单"))
        assertEquals(listOf(2, 4, 6, 8), BenbuScheduleParser.parseWeeksString("2-8双"))
        assertEquals(listOf(1, 3, 5, 7), BenbuScheduleParser.parseWeeksString("1-8", "单周"))
        assertEquals(listOf(2, 4, 6, 8), BenbuScheduleParser.parseWeeksString("1-8", "双周"))
        assertEquals(emptyList<Int>(), BenbuScheduleParser.parseWeeksString(""))
        assertEquals(emptyList<Int>(), BenbuScheduleParser.parseWeeksString("8-1"))
        assertEquals(emptyList<Int>(), BenbuScheduleParser.parseWeeksString("abc"))
    }

    @Test
    fun parityFilterSkipsEvenStartWhenOddRequested() {
        // 单周从偶数起跳时顺延到下一个奇数，不是从偶数开始
        assertEquals(listOf(3, 5, 7), BenbuScheduleParser.parseWeeksString("2-8", "单周"))
        assertEquals(listOf(4, 6, 8), BenbuScheduleParser.parseWeeksString("3-8", "双周"))
    }

    @Test
    fun weeksAreDeduplicatedAndSorted() {
        assertEquals(listOf(1, 2, 3, 4, 5), BenbuScheduleParser.parseWeeksString("3,1,2,1-5"))
    }
    // ------------------------------------------------------------ 实验课

    /** 实验课表：两行表头 + 数据行。 */
    private fun experimentPage(vararg rows: String): String = buildString {
        append("<html><body><table class=\"dataTable\">")
        append("<tr><th>时段</th><th>节次</th>")
        for (day in 1..7) append("<th>星期$day</th>")
        append("</tr><tr><th></th><th></th>")
        for (day in 1..7) append("<th></th>")
        append("</tr>")
        rows.forEach { append(it) }
        append("</table></body></html>")
    }

    private fun experimentRow(label: String, periodRange: String, vararg cells: String): String = buildString {
        append("<tr><td>").append(label).append("</td><td>").append(periodRange).append("</td>")
        cells.forEach { append("<td>").append(it).append("</td>") }
        append("</tr>")
    }

    @Test
    fun parsesExperimentWithExplicitTime() {
        val html = experimentPage(
            experimentRow(
                "下午", "第7,8节",
                "大学物理实验<br>黄丽 [14周第7，8节] 上课形式[线下]L619，15:40-18:10",
                "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val course = BenbuScheduleParser.parseExperimentHtml(html).single()
        assertEquals("大学物理实验", course.name)
        assertEquals("黄丽", course.teacher)
        assertEquals(listOf(14), course.weeks)
        assertEquals("15:40", course.startTime)
        assertEquals("18:10", course.endTime)
        assertEquals(-1, course.begin)
        assertEquals(-1, course.last)
        assertEquals("L619", course.classroom)
        assertEquals(1, course.dow)
    }

    @Test
    fun parsesExperimentFromCombinedLine() {
        val html = experimentPage(
            experimentRow(
                "下午", "第7,8节",
                "用拉伸法测定弹性模量 姚凤凤 [11周第7，8节] 上课形式[线下]L622，15:40-18:10",
                "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val course = BenbuScheduleParser.parseExperimentHtml(html).single()
        assertEquals("用拉伸法测定弹性模量", course.name)
        assertEquals("姚凤凤", course.teacher)
        assertEquals(listOf(11), course.weeks)
        assertEquals("L622", course.classroom)
    }

    @Test
    fun experimentWithoutTimeFallsBackToPeriods() {
        val html = experimentPage(
            experimentRow(
                "下午", "第7,8节",
                "&nbsp;", "电路实验<br>张三 [9周第5，6节] 上课形式[线下]E301",
                "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        val course = BenbuScheduleParser.parseExperimentHtml(html).single()
        assertEquals(5, course.begin)
        assertEquals(2, course.last)
        assertNull(course.startTime)
        assertNull(course.endTime)
        assertEquals("E301", course.classroom)
        assertEquals(2, course.dow)
    }

    @Test
    fun experimentDetailWithoutTitleIsDropped() {
        val html = experimentPage(
            experimentRow(
                "下午", "第7,8节",
                "黄丽 [14周第7，8节] 上课形式[线下]L619，15:40-18:10",
                "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;", "&nbsp;",
            ),
        )
        assertTrue(BenbuScheduleParser.parseExperimentHtml(html).isEmpty())
    }

    @Test
    fun experimentPageWithoutDataTableYieldsNothing() {
        assertTrue(BenbuScheduleParser.parseExperimentHtml("<html><body></body></html>").isEmpty())
        assertTrue(BenbuScheduleParser.parseExperimentHtml("").isEmpty())
    }

    @Test
    fun experimentRowsWithTooFewCellsAreIgnored() {
        val html = experimentPage("<tr><td>上午</td><td>第1,2节</td><td>课程</td></tr>")
        assertTrue(BenbuScheduleParser.parseExperimentHtml(html).isEmpty())
    }
}
