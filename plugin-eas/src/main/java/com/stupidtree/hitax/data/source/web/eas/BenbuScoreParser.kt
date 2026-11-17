package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.hitax.data.model.eas.CourseScoreItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.math.roundToInt

/**
 * 本部（哈尔滨）教务成绩页解析。
 *
 * 逐条复刻 HITA_Android v3.1.2 `BenbuScoreParser.parseGradesHtml`：
 * 页面里可能有多张表（导航、统计、成绩），这里按"表头同时含课程名称与成绩列 + 解析出最多行"挑一张；
 * 成绩列本身可能是字母等级，教务把原始数字成绩放在该单元格**前一个注释节点**里。
 *
 * 与上游的差异（均为契约适配，解析语义不变）：
 * - 契约层 `credits` 是 Int，原始小数写入 `creditsText`，`credits` 取四舍五入；
 * - 上游 `findScoreTable` 未被调用，未移植。
 */
object BenbuScoreParser {

    private const val COURSE_NAME_HEADER = "课程名称"
    private val SCORE_HEADERS = listOf("最终成绩", "成绩", "总成绩")

    fun parseGradesHtml(html: String): List<CourseScoreItem> {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table").toList()
        if (tables.isEmpty()) return emptyList()

        var bestResult: List<CourseScoreItem> = emptyList()

        tables.forEach { table ->
            val rows = table.select("tr")
            if (rows.size < 2) return@forEach

            // 表头行可能不在第 0 行（前面有标题行），逐行找
            val headerIndex = rows.indexOfFirst { row ->
                val headerTexts = row.select("th, td").map { normalizeHeader(it.text()) }
                headerTexts.any { it.contains(COURSE_NAME_HEADER) } &&
                    headerTexts.any { header -> SCORE_HEADERS.any(header::contains) }
            }
            if (headerIndex < 0 || headerIndex >= rows.lastIndex) return@forEach

            val headers = rows[headerIndex].select("th, td").map { normalizeHeader(it.text()) }
            val tableResult = mutableListOf<CourseScoreItem>()

            for (rowIndex in (headerIndex + 1) until rows.size) {
                val cells = rows[rowIndex].select("td")
                if (cells.isEmpty()) continue

                val values = linkedMapOf<String, String>()
                val bound = minOf(headers.size, cells.size)
                for (cellIndex in 0 until bound) {
                    values[headers[cellIndex]] = cells[cellIndex].text().trim()
                }

                val courseName = getValue(values, COURSE_NAME_HEADER)
                if (courseName.isBlank()) continue

                val scoreIndex = SCORE_HEADERS
                    .map { headers.indexOf(normalizeHeader(it)) }
                    .firstOrNull { it >= 0 }
                val creditsRaw = getValue(values, "学分")

                tableResult.add(CourseScoreItem().apply {
                    termName = getValue(values, "学年学期").ifBlank { null }
                    courseCode = getValue(values, "课程代码", "课程号").ifBlank { null }
                    this.courseName = courseName
                    courseProperty = getValue(values, "课程性质").ifBlank { null }
                    courseCategory = getValue(values, "课程类别").ifBlank { null }
                    schoolName = getValue(values, "开课院系", "开课学院").ifBlank { null }
                    assessMethod = getValue(values, "是否考试课", "考核方式").ifBlank { null }

                    creditsText = creditsRaw.ifBlank { null }
                    credits = creditsRaw.toFloatOrNull()?.roundToInt() ?: 0

                    finalScoresText = getValue(values, "最终成绩", "成绩", "总成绩").ifBlank { null }
                    finalScores = parseGradeToInt(finalScoresText.orEmpty())
                    numericScore = scoreIndex?.takeIf { it >= 0 }?.let { cells.getOrNull(it) }?.let(::parseNumericScore)
                    hours = getValue(values, "学时").toIntOrNull() ?: 0
                })
            }

            // 多张表都可能像成绩表，取解析出最多行的那张
            if (tableResult.size > bestResult.size) bestResult = tableResult
        }

        return bestResult
    }

    private fun getValue(values: Map<String, String>, vararg keys: String): String {
        keys.forEach { key ->
            values[normalizeHeader(key)]?.let { return it.trim() }
        }
        return ""
    }

    private fun normalizeHeader(raw: String): String =
        raw.replace(Regex("\\s+"), "")
            .replace("：", "")
            .replace(":", "")
            .trim()

    /**
     * 取原始数字成绩。
     *
     * 先看成绩单元格前一个注释节点（本部教务把 `<td>85</td>` 塞进注释里），
     * 再退回单元格自身文本；两者都必须是 0~100 的有限数。
     */
    private fun parseNumericScore(cell: Element?): Double? {
        if (cell == null) return null

        var previous = cell.previousSibling()
        while (previous is TextNode && previous.text().isBlank()) {
            previous = previous.previousSibling()
        }

        val commentScore = (previous as? Comment)?.let { comment ->
            val doc = Jsoup.parse("<table><tr>${comment.data}</tr></table>")
            val cells = doc.select("table > tbody > tr > td")
            if (cells.size == 1) cells.first()?.text()?.trim()?.toDoubleOrNull() else null
        }?.takeIf { it.isFinite() && it in 0.0..100.0 }

        return commentScore
            ?: cell.text().trim().toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.0..100.0 }
    }

    /** 成绩文本 → 百分制整数；无法识别返回 -1（与上游一致）。 */
    internal fun parseGradeToInt(raw: String): Int {
        val value = raw.trim()
        if (value.isEmpty()) return -1

        value.toDoubleOrNull()?.let { return it.toInt() }

        return when (value.uppercase()) {
            "A+" -> 95
            "A" -> 90
            "A-" -> 85
            "B+" -> 85
            "B" -> 80
            "B-" -> 75
            "C+" -> 75
            "C" -> 70
            "C-" -> 65
            "D+" -> 65
            "D" -> 60
            "D-" -> 55
            "F" -> 0
            "合格", "通过" -> 60
            "不合格", "未通过" -> 0
            else -> -1
        }
    }
}