package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.hitax.data.model.eas.ExamItem
import com.stupidtree.hitax.data.model.eas.TermItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 本部（哈尔滨）考试页解析。
 *
 * 逐条复刻 HITA_Android v3.1.2 `BenbuExamParser`：
 * - 先按表头定位"课程 + 时间/日期"两列，再按表头位置取地点与考试类型；
 * - 没有表头的旧页面按固定列位（课程 1、时间 5、地点 3、类型 6）兜底；
 * - 空数据提示页返回空列表，格式无法识别则抛出明确异常（供查询层区分"没数据"与"页面坏了"）；
 * - 不按周数推算、不补造尚未公布的考试时间。
 *
 * 与上游的唯一差异：本模块 `minSdk 23` 且未开启 core library desugaring，
 * 不能用 `java.time`，日期/时间的合法性校验改为等价的纯算术实现，输出格式保持一致。
 */
internal object BenbuExamParser {

    private data class Columns(
        val course: Int,
        val time: Int,
        val location: Int? = null,
        val date: Int? = null,
        val type: Int? = null,
    )

    /** 供登录失效判定使用：导航文字不能掩盖一张有效的考试表。 */
    fun hasExamTable(doc: Document): Boolean =
        doc.select("tr").any { columns(cells(it)) != null }

    fun parse(html: String, requestedType: String, term: TermItem): List<ExamItem> {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table").map { table ->
            table to table.select("tr").toList().filter { it.parents().any { parent -> parent == table } }
        }

        val withHeader = tables.firstNotNullOfOrNull { (table, rows) ->
            rows.withIndex().firstNotNullOfOrNull { (index, row) ->
                columns(cells(row))?.let { Triple(table, index, it) }
            }
        }

        val table: Element
        val headerIndex: Int
        val layout: Columns
        if (withHeader != null) {
            table = withHeader.first
            headerIndex = withHeader.second
            layout = withHeader.third
        } else {
            // 兼容无表头的旧页面；六列时考试类型由请求参数补足
            table = tables.firstOrNull { (candidate, rows) ->
                candidate.hasClass("bot_line") && rows.any {
                    val values = cells(it)
                    values.size >= 6 && values[0].text().trim().toIntOrNull() != null
                }
            }?.first ?: run {
                require(isEmptyMessage(doc.text())) { "考试页面格式无法识别，请稍后重试" }
                return emptyList()
            }
            headerIndex = -1
            layout = Columns(course = 1, time = 5, location = 3, type = 6)
        }

        val rows = tables.first { it.first == table }.second.drop(headerIndex + 1)

        return rows.mapNotNull { row ->
            val values = cells(row)
            if (values.isEmpty() || row.text().isBlank() || isEmptyMessage(row.text())) return@mapNotNull null
            if (columns(values) != null || (values.size == 1 && values[0].hasAttr("colspan"))) return@mapNotNull null
            if (headerIndex < 0 && values[0].text().trim().toIntOrNull() == null) return@mapNotNull null
            require(values.size > maxOf(layout.course, layout.time)) { "考试记录字段不完整，请稍后重试" }

            val courseName = values[layout.course].text().trim()
            if (courseName.isBlank()) return@mapNotNull null

            val rawTime = listOfNotNull(
                layout.date?.let { values.getOrNull(it)?.text() },
                values[layout.time].text(),
            ).joinToString(" ")
            val (date, time) = parseDateTime(rawTime)
            val rowType = layout.type?.let { values.getOrNull(it)?.text()?.trim() }.orEmpty()

            ExamItem().apply {
                this.courseName = courseName
                examLocation = layout.location?.let { values.getOrNull(it)?.text()?.trim() }
                examDate = date
                examTime = time
                examType = rowType.takeIf { value -> listOf("期中", "期末", "补考").any(value::contains) }
                    ?: requestedType
                campusName = "本部"
                termName = term.name
            }
        }
    }

    private fun cells(row: Element): List<Element> =
        row.children().toList().filter { it.tagName() == "td" || it.tagName() == "th" }

    private fun columns(cells: List<Element>): Columns? {
        val labels = cells.map { it.text().replace(Regex("[\\s\u00a0\u3000]+"), "") }

        val course = labels.indexOfFirst { it in listOf("课程名称", "课程名", "考试科目") }
        val time = labels.indexOfFirst {
            it.contains("考试具体时间") || it.contains("考试日期时间") ||
                (it.contains("考试时间") && !it.contains("时间段"))
        }
        val date = labels.indexOfFirst { it.contains("考试日期") && !it.contains("时间") }

        if (course < 0 || (time < 0 && date < 0)) return null

        return Columns(
            course = course,
            time = if (time >= 0) time else date,
            date = date.takeIf { it >= 0 && it != time && time >= 0 },
            location = labels.indexOfFirst {
                it.contains("考试地点") || it.contains("考试教室") || it == "考场"
            }.takeIf { it >= 0 },
            type = labels.indexOfFirst { it in listOf("考试时间段", "考试类型", "考试类别") }
                .takeIf { it >= 0 },
        )
    }

    private fun isEmptyMessage(text: String): Boolean {
        val compact = text.replace(Regex("[\\s\u00a0\u3000]+"), "")
        return listOf(
            "暂无数据", "无数据", "暂无考试", "无考试安排", "未查询到",
            "没有查询到", "没有相关数据", "共0条",
        ).any(compact::contains)
    }

    /** 从"2026年10月31日 16:00-18:00"这类文本里取 (日期, 时间)。 */
    private fun parseDateTime(raw: String): Pair<String, String> {
        val text = raw.replace('：', ':').replace('\u00a0', ' ').replace('\u3000', ' ')

        val dateMatch = Regex("""(?<!\d)(\d{4})\s*(?:年|[-/.])\s*(\d{1,2})\s*(?:月|[-/.])\s*(\d{1,2})日?""").find(text)
        val date = dateMatch?.let {
            val year = it.groupValues[1].toInt()
            val month = it.groupValues[2].toInt()
            val day = it.groupValues[3].toInt()
            if (isValidDate(year, month, day)) formatDate(year, month, day) else null
        }.orEmpty()

        val timeMatch = Regex(
            """(?<!\d)(\d{1,2})\s*:\s*(\d{2})\s*(?:-|－|—|–|~|～|至|到)\s*(\d{1,2})\s*:\s*(\d{2})(?!\d)""",
        ).find(text)
        val time = timeMatch?.let {
            val fromHour = it.groupValues[1].toInt()
            val fromMinute = it.groupValues[2].toInt()
            val toHour = it.groupValues[3].toInt()
            val toMinute = it.groupValues[4].toInt()
            if (fromHour > 23 || toHour > 23 || fromMinute > 59 || toMinute > 59) {
                null
            } else {
                formatTime(fromHour, fromMinute) + "-" + formatTime(toHour, toMinute)
            }
        }.orEmpty()

        return date to time
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (month !in 1..12 || day < 1) return false
        return day <= daysInMonth(year, month)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    private fun formatDate(year: Int, month: Int, day: Int): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    private fun formatTime(hour: Int, minute: Int): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}