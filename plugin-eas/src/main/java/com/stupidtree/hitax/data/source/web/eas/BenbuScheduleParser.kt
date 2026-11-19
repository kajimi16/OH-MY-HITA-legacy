package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.hitax.data.model.eas.CourseItem
import org.jsoup.Jsoup

/**
 * 本部（哈尔滨）教务课表解析。
 *
 * 逐条复刻 HITA_Android v3.1.2 `BenbuScheduleParser.parseScheduleHtml` 及其私有辅助函数：
 * 表格 `table.addlist_01`、`dow = 列号 - 1`、节次取第 2 列、单元格按 `<br>` 切分后再按
 * "教师[周次]周地点" 的括号结构解析。
 *
 * 与原实现的差异只有日志：上游的 `[DBG] dow=5/6 week=7` 调试输出和 `LogUtils` 全部去掉，
 * 解析结果本身保持一致。
 */
object BenbuScheduleParser {

    /** 周次标记：`[1-8]`、`[1-8]单`、`[1-8]周`、`[1,3,5]周`…… */
    private val WEEK_PATTERN = Regex("""\[([^\]]+)\](?:单|双)?周(?:\((?:单|双)\))?""")

    /** 单条 "教师[周次](单/双)周地点" 的拆解。 */
    private val ELEMENT_PATTERN =
        Regex("""([^,，\[]*)\[([^\]]+?)\](?:(单|双)周)?(?:\((单|双)\))?([^,，]*)""")

    private val BREAK_PATTERN = Regex("""<\s*/?\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE)
    private val ANY_TAG_PATTERN = Regex("<[^>]+>")

    /**
     * 解析课表页 HTML。
     *
     * @return 解析出的课程条目；页面里没有 `table.addlist_01` 时返回空列表。
     */
    fun parseScheduleHtml(html: String): List<CourseItem> {
        val result = mutableListOf<CourseItem>()
        val doc = Jsoup.parse(html)
        val table = doc.select("table.addlist_01").firstOrNull() ?: return result
        val rows = table.select("tr")

        // 第 0 行是表头
        for (rowIndex in 1 until rows.size) {
            val cells = rows[rowIndex].select("td,th")
            if (cells.size < 3) continue

            val periodInfo = cells.getOrNull(1)?.text()?.trim().orEmpty()

            // 第 0 列时间段、第 1 列节次，之后每列一天
            for (cellIndex in 2 until cells.size) {
                val cell = cells[cellIndex]
                val cellText = cell.text().replace("\u00A0", " ").trim()
                if (cellText.isBlank() || cellText.length < 2) continue

                val dow = cellIndex - 1
                result.addAll(parseCellCourses(cell.html(), dow, rowIndex, periodInfo))
            }
        }
        return result
    }

    /** 单个单元格 → 若干课程条目。 */
    private fun parseCellCourses(
        cellHtml: String,
        dow: Int,
        periodHint: Int,
        periodInfo: String,
    ): List<CourseItem> {
        val courses = mutableListOf<CourseItem>()

        // 去掉超链接整段（通常是课程详情链接，正文在链接外重复出现）
        var cleanedHtml = cellHtml.replace(Regex("<a[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
        // 保留 <br> 用于分行，其余标签全部剥掉
        cleanedHtml = cleanedHtml.replace(Regex("<(?!/?br)[^>]+>", RegexOption.IGNORE_CASE), "")

        val parts = cleanedHtml.split(BREAK_PATTERN)
            .map { it.replace(ANY_TAG_PATTERN, "").replace("\u00A0", " ").trim() }
            .filter { it.isNotBlank() && it != "&nbsp;" }

        if (parts.isEmpty()) return courses

        var i = 0
        while (i < parts.size) {
            val line = parts[i]

            // 只有周次没有课程名的行（续行）直接跳过
            if (WEEK_PATTERN.containsMatchIn(line)) {
                i++
                continue
            }

            val courseName = line
            i++

            val teacherLocationLines = mutableListOf<String>()
            while (i < parts.size) {
                val nextLine = parts[i]
                if (WEEK_PATTERN.containsMatchIn(nextLine)) {
                    teacherLocationLines.add(nextLine)
                    i++
                } else if (
                    teacherLocationLines.isNotEmpty() &&
                    !parts.getOrNull(i + 1).orEmpty().let { WEEK_PATTERN.containsMatchIn(it) }
                ) {
                    // 紧跟在教师/地点行之后、且再下一行不是周次 → 视为本行余下部分，一并吞掉
                    teacherLocationLines.add(nextLine)
                    i++
                    break
                } else {
                    break
                }
            }

            if (teacherLocationLines.isNotEmpty()) {
                val combined = teacherLocationLines.joinToString("")
                val parsedForCourse = mutableListOf<CourseItem>()
                splitTeacherLocationPairs(combined).forEach { element ->
                    parsedForCourse.addAll(parseCourseElement(courseName, element, dow, periodHint, periodInfo))
                }
                courses.addAll(normalizeCourseElementParts(parsedForCourse))
            }
        }

        return courses
    }

    /**
     * 按"括号外的逗号"切分多段教师/地点。
     * 括号内（周次）的逗号不切，例如 `[1,3,5]周` 保持完整。
     */
    private fun splitTeacherLocationPairs(text: String): List<String> {
        val elements = mutableListOf<String>()
        val current = StringBuilder()
        var bracketDepth = 0

        for (char in text) {
            when (char) {
                '[' -> bracketDepth++
                ']' -> bracketDepth--
                ',', '，' -> {
                    if (bracketDepth == 0) {
                        if (current.isNotEmpty()) {
                            elements.add(current.toString().trim())
                            current.clear()
                        }
                        continue
                    }
                }
            }
            current.append(char)
        }

        if (current.isNotEmpty()) elements.add(current.toString().trim())
        return elements
    }

    /** 单段 "教师[周次]周地点" → 课程条目（可能因括号重复而产出多条）。 */
    private fun parseCourseElement(
        courseName: String,
        element: String,
        dow: Int,
        periodHint: Int,
        periodInfo: String,
    ): List<CourseItem> {
        val matches = ELEMENT_PATTERN.findAll(element).toList()
        if (matches.isEmpty()) return emptyList()

        val result = mutableListOf<CourseItem>()

        for (match in matches) {
            val teacherPart = match.groupValues[1].trim()
            val weeksStr = match.groupValues[2].trim()
            val oddEvenOutside = match.groupValues[3].trim()
            val oddEvenInside = match.groupValues[4].trim()
            var locationPart = match.groupValues[5].trim()

            // 地点前可能残留 "周" 字（如 "单周A302" 被切成 "周A302"）
            locationPart = locationPart.removePrefix("周").trim()

            val oddEvenHint = when {
                oddEvenOutside.isNotBlank() -> oddEvenOutside + "周"
                oddEvenInside.isNotBlank() -> oddEvenInside + "周"
                else -> ""
            }

            val weeks = parseWeeksString(weeksStr, oddEvenHint)
            val period = resolvePeriod(periodInfo, periodHint)

            result.add(CourseItem().apply {
                code = null
                name = courseName
                this.weeks = weeks
                teacher = sanitizeTeacher(courseName, teacherPart)
                classroom = locationPart.ifBlank { null }
                this.dow = dow
                begin = period.first
                last = period.second
            })
        }

        return result
    }

    /**
     * 同一课程被拆成多段（单双周不同教室等）时，用唯一值补齐缺失字段。
     * 教师/教室各只有一个候选时才回填，多候选保持为空，避免张冠李戴。
     */
    private fun normalizeCourseElementParts(items: List<CourseItem>): List<CourseItem> {
        if (items.size <= 1) return items

        val teacherFallback = items.firstOrNull { !it.teacher.isNullOrBlank() }?.teacher
        val classroomFallback = items.mapNotNull { it.classroom?.takeIf(String::isNotBlank) }
            .distinct()
            .singleOrNull()

        items.forEach { item ->
            if (item.teacher.isNullOrBlank() && !teacherFallback.isNullOrBlank()) {
                item.teacher = teacherFallback
            }
            if (item.classroom.isNullOrBlank() && !classroomFallback.isNullOrBlank()) {
                item.classroom = classroomFallback
            }
        }
        return items
    }

    /**
     * 解析节次，返回 `起始节 to 节数`。
     * 支持 `第1,2节`、`1-4`、`第3节`；无可用信息时退化为 `行号 to 2`（与上游一致）。
     */
    private fun resolvePeriod(periodInfo: String, periodHint: Int): Pair<Int, Int> {
        val normalized = periodInfo
            .replace("\u00A0", "")
            .replace("第", "")
            .replace("节", "")
            .replace("，", ",")
            .trim()

        val range = Regex("""(\d+)\s*[-,]\s*(\d+)""").find(normalized)
        if (range != null) {
            val begin = range.groupValues[1].toIntOrNull()
            val end = range.groupValues[2].toIntOrNull()
            if (begin != null && end != null && end >= begin) {
                return begin to (end - begin + 1)
            }
        }

        val single = Regex("""(\d+)""").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (single != null && single > 0) return single to 1

        return periodHint to 2
    }

    /**
     * 周次串 → 升序周次列表。
     *
     * 形如 `1-8`、`1,3,5`、`1-8单`、`2-8双`；单双周由外部提示优先，其次从串里识别。
     */
    internal fun parseWeeksString(weeksStr: String, oddEvenHint: String = ""): MutableList<Int> {
        val source = weeksStr.replace("\u00A0", " ").replace(" ", "")
        if (source.isBlank()) return mutableListOf()

        val oddEven = when {
            oddEvenHint.contains("单") -> "odd"
            oddEvenHint.contains("双") -> "even"
            source.contains("单") -> "odd"
            source.contains("双") -> "even"
            else -> null
        }

        val normalized = source
            .replace("单周", "")
            .replace("双周", "")
            .replace("单", "")
            .replace("双", "")

        val weeks = linkedSetOf<Int>()

        normalized.split(Regex("[,，]")).forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isBlank()) return@forEach

            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                val start = range.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val end = range.getOrNull(1)?.toIntOrNull() ?: return@forEach
                if (start > end) return@forEach

                val step = if (oddEven == null) 1 else 2
                var first = start
                if (oddEven == "odd" && first % 2 == 0) first += 1
                if (oddEven == "even" && first % 2 != 0) first += 1
                for (week in first..end step step) weeks.add(week)
            } else {
                val week = trimmed.toIntOrNull() ?: return@forEach
                if (oddEven == null ||
                    (oddEven == "odd" && week % 2 == 1) ||
                    (oddEven == "even" && week % 2 == 0)
                ) {
                    weeks.add(week)
                }
            }
        }

        return weeks.sorted().toMutableList()
    }

    /**
     * 教师名清洗：
     * - 去掉 `【…】` 前缀（课程类型/模块标记）；
     * - 去掉重复出现的课程名；
     * - 只取 `/` 之前的第一位教师；
     * - 去掉 `第X批` 前缀。
     */
    private fun sanitizeTeacher(courseName: String, teacherRaw: String?): String? {
        val source = teacherRaw?.trim().orEmpty()
        if (source.isBlank()) return null

        var candidate = source
        if (candidate.startsWith("【") && candidate.contains("】")) {
            val closeIndex = candidate.indexOf('】')
            if (closeIndex >= 0 && closeIndex < candidate.length - 1) {
                candidate = candidate.substring(closeIndex + 1).trimStart('/', '／', ' ', '\t')
            }
        }

        if (candidate.startsWith(courseName)) {
            candidate = candidate.removePrefix(courseName).trimStart('/', '／', ' ', '\t')
        }

        return candidate.substringBefore('/')
            .trim()
            .replace(Regex("^第[一二三四五六七八九十0-9]+批"), "")
            .trimStart('/', '／', ' ', '\t')
            .trim()
            .ifBlank { null }
    }
    // ------------------------------------------------------------ 实验课

    /** 实验课时间标记：`[14周第7，8节]`。 */
    private val EXPERIMENT_SCHEDULE_PATTERN = Regex("""\[\d+周第[\d，]+节\]""")

    /**
     * 解析实验课表 HTML（接口 `/xskb/queryXszkb`）。
     *
     * 表格 `table.dataTable`：前两行是表头，第 0 列上午/下午/晚上、第 1 列节次，
     * 之后每列一天；单元格内以 `<br>` 分隔"课程名"与"教师 [周次] 上课形式[形式]地点，时间"。
     *
     * 与主课表不同，实验课带具体时间（`15:40-18:10`），此时 `begin/last` 置 -1，
     * 时间写入 `startTime/endTime`；没有具体时间才回退到节次。
     *
     * 解析异常不抛出：实验课表属于附加信息，坏一页不该拖垮整个导入。
     */
    fun parseExperimentHtml(html: String): List<CourseItem> {
        val result = mutableListOf<CourseItem>()
        val doc = Jsoup.parse(html)
        val table = doc.select("table.dataTable").firstOrNull() ?: return result
        val rows = table.select("tr")

        // 跳过两行表头
        for (rowIndex in 2 until rows.size) {
            val cells = rows[rowIndex].select("td")
            if (cells.size < 9) continue

            val periodLabel = cells.getOrNull(0)?.text()?.trim().orEmpty()
            val periodRange = cells.getOrNull(1)?.text()?.trim().orEmpty()

            for (cellIndex in 2 until cells.size) {
                val cell = cells[cellIndex]
                val cellText = cell.text().replace("\u00A0", " ").trim()
                if (cellText.isBlank() || cellText == "&nbsp;") continue

                val dow = cellIndex - 1
                result.addAll(parseExperimentCell(cell.html(), dow, periodLabel, periodRange))
            }
        }
        return result
    }

    /** 单元格 → 实验课条目。 */
    private fun parseExperimentCell(
        cellHtml: String,
        dow: Int,
        periodLabel: String,
        periodRange: String,
    ): List<CourseItem> {
        val courses = mutableListOf<CourseItem>()
        val parts = cellHtml.split(BREAK_PATTERN)
            .map { Jsoup.parseBodyFragment(it).text().replace('\u00A0', ' ').trim() }
            .filter { it.isNotBlank() }

        var courseName: String? = null

        for (line in parts) {
            val schedule = EXPERIMENT_SCHEDULE_PATTERN.find(line)
            if (schedule == null) {
                // 没有时间标记的行一律视为课程标题（不论长短、是否含"周""节"字样）
                courseName = line
                continue
            }

            val beforeSchedule = line.substring(0, schedule.range.first).trim()
            if (beforeSchedule.contains(' ')) {
                // 课程名与教师挤在同一行：不继承上一条标题
                val course = parseExperimentDetailFromCombined(line, dow, periodLabel, periodRange)
                courseName = course?.name
                if (course != null) courses.add(course)
            } else {
                // 明细行归属最近的标题行
                val name = courseName ?: continue
                parseExperimentDetail(name, line, dow, periodLabel, periodRange)?.let(courses::add)
            }
        }
        return courses
    }

    /** 单独标题行 + 明细行 → 实验课条目。 */
    private fun parseExperimentDetail(
        courseName: String,
        detail: String,
        dow: Int,
        periodLabel: String,
        periodRange: String,
    ): CourseItem? {
        // 格式：教师 [周次] 节次 上课形式[形式]地点，时间
        val teacherEndIndex = detail.indexOf(" [")
        if (teacherEndIndex <= 0) return null

        val weekMatch = Regex("""\[(\d+)周第([\d，]+)节\]""").find(detail) ?: return null
        val weekNum = weekMatch.groupValues[1].toIntOrNull() ?: return null

        val course = CourseItem()
        course.name = courseName
        course.teacher = detail.substring(0, teacherEndIndex).trim()
        course.weeks = mutableListOf(weekNum)
        course.dow = dow
        applyExperimentTimeAndPlace(course, detail, weekMatch.groupValues[2])
        return course
    }

    /** 课程名 + 教师 + 明细挤在一行 → 实验课条目。 */
    private fun parseExperimentDetailFromCombined(
        combined: String,
        dow: Int,
        periodLabel: String,
        periodRange: String,
    ): CourseItem? {
        // 格式：课程名 教师 [周次] 节次 上课形式[形式]地点，时间
        val bracketIndex = combined.indexOf(" [")
        if (bracketIndex <= 0) return null

        val beforeBracket = combined.substring(0, bracketIndex).trim()
        val lastSpaceIndex = beforeBracket.lastIndexOf(" ")
        if (lastSpaceIndex <= 0) return null

        val weekMatch = Regex("""(\d+)周第([\d，]+)节""").find(combined.substring(bracketIndex + 1)) ?: return null
        val weekNum = weekMatch.groupValues[1].toIntOrNull() ?: return null

        val course = CourseItem()
        course.name = beforeBracket.substring(0, lastSpaceIndex).trim()
        course.teacher = beforeBracket.substring(lastSpaceIndex + 1).trim()
        course.weeks = mutableListOf(weekNum)
        course.dow = dow
        applyExperimentTimeAndPlace(course, combined.substring(bracketIndex + 1), weekMatch.groupValues[2])
        return course
    }

    /**
     * 补齐时间与地点：
     * 有 `HH:mm-HH:mm` 就用具体时间（begin/last = -1），否则回退到"第7，8节"这样的节次范围；
     * 地点取 `上课形式[线下]` 之后或逗号后的段落。
     */
    private fun applyExperimentTimeAndPlace(course: CourseItem, detail: String, periodStr: String) {
        val afterBrackets = detail.substringAfter("]")
        val timeMatch = Regex("""(\d{1,2}:\d{2})-(\d{1,2}:\d{2})""").find(afterBrackets)

        if (timeMatch != null) {
            course.startTime = timeMatch.groupValues[1]
            course.endTime = timeMatch.groupValues[2]
            course.begin = -1
            course.last = -1
        } else {
            val (begin, duration) = parsePeriodRange(periodStr)
            course.begin = begin
            course.last = duration
        }

        val locationMatch = LOCATION_PATTERN.find(
            if (timeMatch != null) afterBrackets else afterBrackets.substringBefore(","),
        )
        if (locationMatch != null) {
            course.classroom = locationMatch.groupValues[1]
                .ifBlank { locationMatch.groupValues[2] }
                .trim()
                .ifBlank { null }
        }
    }

    /** `上课形式[线下]L619` / `，L619` 两种写法的地点。 */
    private val LOCATION_PATTERN = Regex("""上课形式\[[^\]]+\]([^，，]+)[，，]?|，([^，，]+)[，，]?""")

    /** `7，8` → `7 to 2`，与普通课表的 begin/节数 语义一致。 */
    private fun parsePeriodRange(periodStr: String): Pair<Int, Int> {
        val parts = periodStr.split("，", ",")
        val first = parts.firstOrNull()?.toIntOrNull() ?: 1
        val second = parts.getOrNull(1)?.toIntOrNull() ?: first
        return first to (second - first + 1)
    }
}
