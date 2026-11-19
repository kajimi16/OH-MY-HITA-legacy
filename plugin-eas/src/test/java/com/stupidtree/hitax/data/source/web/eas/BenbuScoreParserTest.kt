package com.stupidtree.hitax.data.source.web.eas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenbuScoreParserTest {

    private fun gradesPage(table: String): String = "<html><body>$table</body></html>"

    private val fullHeader =
        "<tr><th>序号</th><th>课程名称</th><th>课程性质</th><th>学分</th><th>最终成绩</th><th>学时</th><th>学年学期</th></tr>"

    @Test
    fun parsesGradesAndKeepsRawScoreText() {
        val html = gradesPage(
            """
            <table class="bot_line">
            $fullHeader
            <tr><td>1</td><td>汇编语言与接口技术</td><td>必修</td><td>3.5</td>
                <!--<td>92</td>--><td>优秀</td><td>64</td><td>2026-2027学年第一学期</td></tr>
            <tr><td>2</td><td>大学英语</td><td>选修</td><td>2</td><td>A-</td><td>32</td>
                <td>2026-2027学年第一学期</td></tr>
            </table>
            """.trimIndent(),
        )
        val items = BenbuScoreParser.parseGradesHtml(html)

        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("汇编语言与接口技术", first.courseName)
        assertEquals("必修", first.courseProperty)
        assertEquals("2026-2027学年第一学期", first.termName)
        assertEquals("3.5", first.creditsText)
        assertEquals(4, first.credits)
        assertEquals("优秀", first.finalScoresText)
        // 上游只映射 合格/通过→60，优秀 不在映射表里，落到 -1（契约层仍靠 numericScore 取原始分）
        assertEquals(-1, first.finalScores)
        // 本部把原始数字分放在成绩单元格前的注释里
        assertEquals(92.0, first.numericScore!!, 0.0001)
        assertEquals(64, first.hours)

        val second = items[1]
        assertEquals("A-", second.finalScoresText)
        assertEquals(85, second.finalScores)
        assertNull(second.numericScore)
        assertEquals(2, second.credits)
    }

    @Test
    fun picksTableWithMostRowsWhenSeveralLookLikeGrades() {
        val html = gradesPage(
            """
            <table>
            $fullHeader
            <tr><td>1</td><td>临时课程</td><td>选修</td><td>1</td><td>90</td><td>16</td><td>2026</td></tr>
            </table>
            <table class="bot_line">
            $fullHeader
            <tr><td>1</td><td>课程甲</td><td>必修</td><td>3</td><td>88</td><td>48</td><td>2026</td></tr>
            <tr><td>2</td><td>课程乙</td><td>必修</td><td>2</td><td>77</td><td>32</td><td>2026</td></tr>
            </table>
            """.trimIndent(),
        )
        val items = BenbuScoreParser.parseGradesHtml(html)
        assertEquals(listOf("课程甲", "课程乙"), items.map { it.courseName })
    }

    @Test
    fun headerRowIsFoundBelowTitleRows() {
        val html = gradesPage(
            """
            <table>
            <tr><td colspan="7">2026-2027学年第一学期成绩单</td></tr>
            $fullHeader
            <tr><td>1</td><td>数据结构</td><td>必修</td><td>4</td><td>95</td><td>64</td><td>2026</td></tr>
            </table>
            """.trimIndent(),
        )
        assertEquals("数据结构", BenbuScoreParser.parseGradesHtml(html).single().courseName)
    }

    @Test
    fun numericTextScoreFillsBothFields() {
        val html = gradesPage(
            """
            <table>
            $fullHeader
            <tr><td>1</td><td>概率论</td><td>必修</td><td>3</td><td>86.5</td><td>48</td><td>2026</td></tr>
            </table>
            """.trimIndent(),
        )
        val item = BenbuScoreParser.parseGradesHtml(html).single()
        assertEquals("86.5", item.finalScoresText)
        assertEquals(86, item.finalScores)
        assertEquals(86.5, item.numericScore!!, 0.0001)
    }

    @Test
    fun pagesWithoutGradeTableYieldNothing() {
        assertTrue(BenbuScoreParser.parseGradesHtml("<html><body><p>无权限</p></body></html>").isEmpty())
        assertTrue(BenbuScoreParser.parseGradesHtml("").isEmpty())
        // 只有表头没有数据行
        assertTrue(BenbuScoreParser.parseGradesHtml(gradesPage("<table>$fullHeader</table>")).isEmpty())
    }

    @Test
    fun rowsWithoutCourseNameAreSkipped() {
        val html = gradesPage(
            """
            <table>
            $fullHeader
            <tr><td>1</td><td></td><td>必修</td><td>3</td><td>88</td><td>48</td><td>2026</td></tr>
            <tr><td>2</td><td>线性代数</td><td>必修</td><td>3</td><td>79</td><td>48</td><td>2026</td></tr>
            </table>
            """.trimIndent(),
        )
        assertEquals(listOf("线性代数"), BenbuScoreParser.parseGradesHtml(html).map { it.courseName })
    }

    @Test
    fun gradeTextMappingMatchesUpstream() {
        assertEquals(95, BenbuScoreParser.parseGradeToInt("A+"))
        assertEquals(85, BenbuScoreParser.parseGradeToInt("B+"))
        assertEquals(60, BenbuScoreParser.parseGradeToInt("D"))
        assertEquals(0, BenbuScoreParser.parseGradeToInt("F"))
        assertEquals(60, BenbuScoreParser.parseGradeToInt("合格"))
        assertEquals(0, BenbuScoreParser.parseGradeToInt("不合格"))
        assertEquals(88, BenbuScoreParser.parseGradeToInt("88"))
        assertEquals(-1, BenbuScoreParser.parseGradeToInt(""))
        assertEquals(-1, BenbuScoreParser.parseGradeToInt("待评教"))
    }
}