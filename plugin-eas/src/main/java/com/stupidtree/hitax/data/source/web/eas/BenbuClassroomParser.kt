package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.hitax.ui.eas.classroom.ClassroomItem
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 本部（哈尔滨）空教室查询页解析。
 *
 * 逐条复刻 HITA_ANDROID v3.1.2 `BenbuClassroomParser`：
 * 表格 `table.dataTable` 中每行一个教室，前面是若干固定列（教室名、容量…），
 * 后面固定 42 列 = 7 天 × 6 个时段；有时段占位图标 `kjs_icon01` 的格子即被占用。
 *
 * 42 列按"每天 6 段"排布：`dow = index / 6 + 1`、`block = index % 6 + 1`，
 * 再展开成具体节次（每段两节）。
 */
object BenbuClassroomParser {

    private const val SCHEDULE_CELL_COUNT = 42

    /** 每段两节。 */
    private fun periodsForBlock(block: Int): List<Int> = when (block) {
        1 -> listOf(1, 2)
        2 -> listOf(3, 4)
        3 -> listOf(5, 6)
        4 -> listOf(7, 8)
        5 -> listOf(9, 10)
        6 -> listOf(11, 12)
        else -> emptyList()
    }

    fun parseEmptyClassroomHtml(html: String): List<ClassroomItem> {
        val result = mutableListOf<ClassroomItem>()
        val doc = Jsoup.parse(html)

        // 页面里可能有多张 dataTable，取列数最多的那张
        val table = doc.select("table.dataTable").maxByOrNull { candidate ->
            candidate.select("tr").maxOfOrNull { row -> row.select("td,th").size } ?: 0
        } ?: return result

        for (row in table.select("tr")) {
            val cells = row.select("td,th")
            if (cells.size < SCHEDULE_CELL_COUNT + 1) continue

            val fixedColumnCount = cells.size - SCHEDULE_CELL_COUNT
            if (fixedColumnCount < 1) continue

            val roomName = cells[0].text().trim().replace("\u00A0", "")
            if (roomName.isBlank()) continue

            // 固定列里第一个能转成数字的当作容量
            val capacity = if (fixedColumnCount > 1) {
                cells.subList(1, fixedColumnCount)
                    .asSequence()
                    .map { it.text().trim().replace("\u00A0", "") }
                    .mapNotNull { text -> text.filter(Char::isDigit).toIntOrNull() }
                    .firstOrNull() ?: 0
            } else {
                0
            }

            val classroom = ClassroomItem().apply {
                name = roomName
                id = roomName
                this.capacity = capacity
                specialClassroom = ""
            }

            for (scheduleIndex in 0 until SCHEDULE_CELL_COUNT) {
                val occupied = isOccupiedCell(cells[fixedColumnCount + scheduleIndex])
                val dow = scheduleIndex / 6 + 1
                val block = scheduleIndex % 6 + 1

                periodsForBlock(block).forEach { period ->
                    classroom.scheduleList.add(JSONObject().apply {
                        put("XQJ", dow)
                        put("XJ", period)
                        put("PKBJ", if (occupied) "占用" else "")
                    })
                }
            }

            result.add(classroom)
        }
        return result
    }

    /** 占位图标判定：`div.kjs_icon.kjs_icon01`，或任意带 `kjs_icon01` class 的元素。 */
    private fun isOccupiedCell(cell: Element): Boolean {
        if (cell.select("div.kjs_icon.kjs_icon01").isNotEmpty()) return true
        return cell.select(".kjs_icon01").any { it.classNames().contains("kjs_icon01") }
    }
}