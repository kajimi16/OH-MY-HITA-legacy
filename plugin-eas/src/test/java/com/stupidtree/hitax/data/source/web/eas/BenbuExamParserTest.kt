package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.hitax.data.model.eas.TermItem
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BenbuExamParserTest {

    private val term = TermItem("2026", "2026-2027学年", "1", "秋季学期")

    private fun page(table: String): String = "<html><body>$table</body></html>"

    private val header = "<tr><th>序号</th><th>课程名称</th><th>考试具体时间</th><th>考试地点</th><th>考试时间段</th></tr>"

    @Test
    fun parsesDateAndTimeFromCombinedCell() {
        val html = page(
            """
            <table class="bot_line">
            $header
            <tr><td>1</td><td>汇编语言与接口技术</td><td>2026年10月31日 16:00-18:00</td><td>G楼 G707</td><td>期末</td></tr>
            </table>
            """.trimIndent(),
        )
        val exam = BenbuExamParser.parse(html, "期末", term).single()

        assertEquals("汇编语言与接口技术", exam.courseName)
        assertEquals("2026-10-31", exam.examDate)
        assertEquals("16:00-18:00", exam.examTime)
        assertEquals("G楼 G707", exam.examLocation)
        assertEquals("期末", exam.examType)
        assertEquals("本部", exam.campusName)
        assertEquals("2026-2027学年秋季学期", exam.termName)
    }

    @Test
    fun splitsSeparateDateAndTimeColumns() {
        val html = page(
            """
            <table class="bot_line">
            <tr><th>课程名称</th><th>考试日期</th><th>考试时间</th><th>考试地点</th></tr>
            <tr><td>大学物理</td><td>2026-11-05</td><td>09:00-11:00</td><td>教学楼 H201</td></tr>
            </table>
            """.trimIndent(),
        )
        val exam = BenbuExamParser.parse(html, "期末", term).single()
        assertEquals("2026-11-05", exam.examDate)
        assertEquals("09:00-11:00", exam.examTime)
        assertEquals("教学楼 H201", exam.examLocation)
    }

    @Test
    fun rowTypeOverridesRequestedTypeWhenRecognizable() {
        val html = page(
            """
            <table class="bot_line">
            <tr><th>课程名称</th><th>考试具体时间</th><th>考试地点</th><th>考试类别</th></tr>
            <tr><td>高等数学</td><td>2026-12-20 14:00-16:00</td><td>B楼 B101</td><td>补考</td></tr>
            </table>
            """.trimIndent(),
        )
        assertEquals("补考", BenbuExamParser.parse(html, "期末", term).single().examType)
    }

    @Test
    fun requestedTypeIsFallbackWhenRowTypeIsUnrecognized() {
        val html = page(
            """
            <table class="bot_line">
            <tr><th>课程名称</th><th>考试具体时间</th><th>考试地点</th><th>考试时间段</th></tr>
            <tr><td>线性代数</td><td>2026-12-22 14:00-16:00</td><td>C楼 C202</td><td>正常考试</td></tr>
            </table>
            """.trimIndent(),
        )
        assertEquals("期末", BenbuExamParser.parse(html, "期末", term).single().examType)
    }

    @Test
    fun invalidDateKeepsTimeButNotDate() {
        val html = page(
            """
            <table class="bot_line">
            $header
            <tr><td>1</td><td>物理实验</td><td>2026-02-31 10:00-12:00</td><td>L301</td><td>期末</td></tr>
            </table>
            """.trimIndent(),
        )
        val exam = BenbuExamParser.parse(html, "期末", term).single()
        assertEquals("", exam.examDate)
        assertEquals("10:00-12:00", exam.examTime)
    }

    @Test
    fun emptyMessagePageYieldsNothing() {
        assertTrue(BenbuExamParser.parse(page("<div>暂无数据</div>"), "期末", term).isEmpty())
        assertTrue(BenbuExamParser.parse(page("<div>共0条记录</div>"), "期末", term).isEmpty())
    }

    @Test
    fun unrecognizablePageFailsLoudly() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BenbuExamParser.parse(page("<div>系统维护中</div>"), "期末", term)
        }
        assertTrue(error.message!!.contains("格式无法识别"))
    }

    @Test
    fun incompleteRowFailsLoudly() {
        val html = page(
            """
            <table class="bot_line">
            <tr><th>课程名称</th><th>考试具体时间</th></tr>
            <tr><td>只有课程名</td></tr>
            </table>
            """.trimIndent(),
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            BenbuExamParser.parse(html, "期末", term)
        }
        assertTrue(error.message!!.contains("字段不完整"))
    }

    @Test
    fun colspanAndHeaderRowsAreSkipped() {
        val html = page(
            """
            <table class="bot_line">
            $header
            <tr><td colspan="5">说明：以教务系统为准</td></tr>
            <tr><td>1</td><td>数据结构</td><td>2026-12-25 09:00-11:00</td><td>D楼 D401</td><td>期末</td></tr>
            </table>
            """.trimIndent(),
        )
        assertEquals(listOf("数据结构"), BenbuExamParser.parse(html, "期末", term).map { it.courseName })
    }

    @Test
    fun hasExamTableDetectsHeaderTableOnly() {
        val withTable = Jsoup.parse(page("<table>$header</table>"))
        val withoutTable = Jsoup.parse(page("<div>登录超时</div>"))
        assertTrue(BenbuExamParser.hasExamTable(withTable))
        assertFalse(BenbuExamParser.hasExamTable(withoutTable))
    }
}