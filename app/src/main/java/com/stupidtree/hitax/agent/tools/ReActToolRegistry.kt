package com.stupidtree.hitax.agent.tools

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.stupidtree.hitax.agent.core.AgentTraceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * ReAct 工具注册表：schema、执行前校验、guard、超时、后处理与截断都在这里统一。
 * 具体工具仍然只实现 [SuspendingReActTool]，不感知提示词或流水线。
 */
class ReActToolRegistry {

    private val specs = LinkedHashMap<String, ToolSpec>()
    private val guards = mutableListOf<ToolGuard>()
    private val postProcessors = mutableListOf<ToolPostProcessor>()

    fun register(spec: ToolSpec) {
        specs[normalizeName(spec.name)] = spec
    }

    /** 兼容旧调用：没有 schema 的工具按原始字符串直接执行。 */
    fun register(name: String, tool: ReActTool) {
        register(name, SuspendingReActTool { input -> tool.execute(input) })
    }

    fun register(name: String, tool: SuspendingReActTool) {
        register(ToolSpec(name = normalizeName(name), description = name, tool = tool))
    }

    fun get(name: String): SuspendingReActTool? = specs[normalizeName(name)]?.tool

    fun spec(name: String): ToolSpec? = specs[normalizeName(name)]

    fun names(): List<String> = specs.keys.toList()

    fun guard(guard: ToolGuard): ReActToolRegistry = apply { guards += guard }

    fun postProcess(processor: ToolPostProcessor): ReActToolRegistry = apply { postProcessors += processor }

    /** 生成给模型的工具目录；描述与参数都来自同一份 [ToolSpec]。 */
    fun catalog(): String = specs.values.joinToString("\n") { spec ->
        val params = spec.parameters.joinToString(", ") { param ->
            "\"${param.name}\":${param.placeholder()}"
        }
        val required = spec.parameters.filter { it.required }.map { it.name }
        val details = spec.parameters
            .filter { it.description.isNotBlank() }
            .joinToString("；") { "${it.name}=${it.description}" }
        buildString {
            append("- ${spec.name}：${spec.description} 动作输入 {$params}")
            if (required.isNotEmpty()) append("；必填：${required.joinToString(",")}")
            if (details.isNotEmpty()) append("；说明：$details")
            append("。")
        }
    }

    suspend fun execute(
        name: String,
        input: ReActToolInput,
        onTrace: (AgentTraceEvent) -> Unit = {},
    ): ToolExecution {
        val spec = specs[normalizeName(name)]
            ?: return ToolExecution("未知工具：$name", error = true)
        val cleaned = clean(input.actionInput)
        val call = ToolCall(spec.name, cleaned)
        return runPipeline(spec, call, onTrace) {
            spec.tool.execute(input.copy(actionInput = cleaned))
        }
    }

    internal suspend fun runPipeline(
        spec: ToolSpec,
        call: ToolCall,
        onTrace: (AgentTraceEvent) -> Unit = {},
        invoke: suspend () -> String?,
    ): ToolExecution {
        for (guard in guards) {
            val decision = runCatching { guard.check(call) }.getOrElse {
                return ToolExecution(
                    "守卫异常：${it.message ?: it::class.java.simpleName}",
                    error = true,
                )
            }
            when (decision) {
                ToolDecision.Allow -> Unit
                is ToolDecision.Deny -> return ToolExecution(
                    "工具被拒绝：${decision.reason}",
                    error = true,
                )
                is ToolDecision.Ask -> return ToolExecution(
                    "工具需要确认：${decision.reason}",
                    error = true,
                )
            }
        }

        val argumentErrors = validate(spec, call.actionInput)
        if (argumentErrors.isNotEmpty()) {
            return ToolExecution("参数错误：${argumentErrors.joinToString("；")}", error = true)
        }

        var result = try {
            onTrace(AgentTraceEvent(stage = "tool", message = "执行工具：${spec.name}", payload = ""))
            ToolExecution(withTimeout(spec.timeoutMs) { invoke() }.orEmpty())
        } catch (e: TimeoutCancellationException) {
            ToolExecution("工具超时：${spec.name}", error = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ToolExecution("工具执行失败：${e.message ?: e::class.java.simpleName}", error = true)
        }

        for (processor in postProcessors) {
            result = runCatching { processor.process(call, result) }.getOrNull() ?: result
        }

        if (result.text.length > spec.maxObservationChars) {
            result = result.copy(
                text = result.text.take(spec.maxObservationChars) + "\n…[truncated]",
                truncated = true,
            )
        }
        return result
    }

    internal fun validate(spec: ToolSpec, actionInput: String): List<String> {
        val cleaned = clean(actionInput)
        // 兼容历史行为：非 JSON 原文交给工具自行解析，不在这里拦。
        if (!cleaned.startsWith("{")) return emptyList()
        val json = runCatching { JsonParser.parseString(cleaned).asJsonObject }.getOrNull()
            ?: return listOf("动作输入不是合法 JSON")

        val errors = mutableListOf<String>()
        spec.parameters.forEach { param ->
            val presentKey = (listOf(param.name) + param.aliases).firstOrNull { json.has(it) }
            if (presentKey == null) {
                if (param.required) errors += "缺少参数 ${param.name}"
                return@forEach
            }
            val value = json.get(presentKey)
            if (!matchesType(value, param.type)) {
                errors += "参数 ${param.name} 应为 ${param.type.name.lowercase()}"
            } else if (param.enumValues.isNotEmpty() && value.asString !in param.enumValues) {
                errors += "参数 ${param.name} 必须是 ${param.enumValues.joinToString("/")}"
            }
        }
        return errors
    }

    private fun matchesType(value: JsonElement?, type: ToolParamType): Boolean {
        val primitive = value?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return false
        return when (type) {
            ToolParamType.STRING -> primitive.isString
            ToolParamType.INTEGER ->
                (primitive.isNumber && primitive.asDouble % 1.0 == 0.0) ||
                    (primitive.isString && primitive.asString.toIntOrNull() != null)
            ToolParamType.NUMBER ->
                primitive.isNumber ||
                    (primitive.isString && primitive.asString.toDoubleOrNull() != null)
            ToolParamType.BOOLEAN ->
                primitive.isBoolean ||
                    (primitive.isString && primitive.asString.lowercase() in setOf("true", "false"))
        }
    }

    private fun clean(actionInput: String): String =
        actionInput.replace(Regex("""```(?:json)?\s*"""), "")
            .replace("```", "")
            .replace("`", "")
            .trim()

    private fun ToolParam.placeholder(): String = when (type) {
        ToolParamType.STRING -> if (enumValues.isEmpty()) "\"...\"" else "\"${enumValues.joinToString("|")}\""
        ToolParamType.INTEGER -> "0"
        ToolParamType.NUMBER -> "0"
        ToolParamType.BOOLEAN -> "false"
    }

    companion object {
        fun normalizeName(name: String): String {
            return name.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
        }

        fun createDefault(): ReActToolRegistry = ReActToolRegistry().apply {
            add(
                ToolSpec(
                    name = "get_timetable",
                    description = "查询今天/明天/昨天等日期的课表；日期由用户消息中的相对时间决定。",
                    tool = GetTimetableTool(),
                ),
            )
            add(
                ToolSpec(
                    name = "add_activity",
                    description = "往当前课表添加一个活动或日程。",
                    parameters = listOf(
                        ToolParam("name", required = true, description = "活动名"),
                        ToolParam("day_offset", ToolParamType.INTEGER, description = "相对今天的天数"),
                        ToolParam("start_time", description = "开始时间，如 15:00"),
                        ToolParam("end_time", description = "结束时间，如 16:00"),
                        ToolParam("place", description = "地点"),
                    ),
                    tool = AddActivityTool(),
                ),
            )
            add(
                ToolSpec(
                    name = "search_empty_classroom",
                    description = "按周次、教学楼和星期查询空教室。",
                    parameters = listOf(
                        ToolParam("week", ToolParamType.INTEGER, description = "周次，默认 1"),
                        ToolParam("building", description = "教学楼名称或 id，可省略"),
                        ToolParam("day", ToolParamType.INTEGER, description = "星期 1-7，可省略"),
                    ),
                    timeoutMs = 120_000,
                    tool = SearchEmptyClassroomTool(),
                ),
            )
            add(
                ToolSpec(
                    name = "search_teacher",
                    description = "按教师姓名检索教师信息。",
                    parameters = listOf(
                        ToolParam("name", required = true, description = "教师姓名", aliases = listOf("teacher_name")),
                    ),
                    timeoutMs = 30_000,
                    tool = SearchTeacherTool(),
                ),
            )
            add(
                ToolSpec(
                    name = "search_external_resource",
                    description = "在 HOA 课程资源中搜索资料。",
                    parameters = listOf(
                        ToolParam("query", required = true, description = "资料关键词", aliases = listOf("keyword", "course_name", "param")),
                    ),
                    tool = SearchExternalResourceTool(),
                ),
            )
            add(
                ToolSpec(
                    name = "search_timetable",
                    description = "在本学期课表中按课程名、教师或地点检索。",
                    parameters = listOf(
                        ToolParam("query", required = true, description = "检索关键词", aliases = listOf("keyword", "param", "name")),
                    ),
                    tool = SearchTimetableTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "search_course",
                    description = "在 HOA 课程库中搜索课程。",
                    parameters = listOf(
                        ToolParam("query", required = true, description = "课程关键词", aliases = listOf("keyword", "param")),
                    ),
                    tool = SearchCourseTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "get_course_detail",
                    description = "读取课程详情，可按字符区间分段读取。",
                    parameters = listOf(
                        ToolParam("course_code", required = true, description = "课程代码", aliases = listOf("code", "param")),
                        ToolParam("start", ToolParamType.INTEGER, description = "起始字符"),
                        ToolParam("end", ToolParamType.INTEGER, description = "结束字符"),
                        ToolParam("query", description = "关注点关键词"),
                    ),
                    maxObservationChars = 6000,
                    tool = GetCourseDetailTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "web_search",
                    description = "搜索公开网页。",
                    parameters = listOf(
                        ToolParam("param", required = true, description = "搜索词", aliases = listOf("query")),
                    ),
                    tool = WebSearchTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "rag_search",
                    description = "在知识库中检索问题答案。",
                    parameters = listOf(
                        ToolParam("query", required = true, description = "问题或关键词", aliases = listOf("keyword", "param")),
                    ),
                    tool = RagSearchTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "crawl_page",
                    description = "抓取单个网页，可按字符区间分段读取。",
                    parameters = listOf(
                        ToolParam("url", required = true, description = "网页地址"),
                        ToolParam("start", ToolParamType.INTEGER, description = "起始字符"),
                        ToolParam("end", ToolParamType.INTEGER, description = "结束字符"),
                    ),
                    maxObservationChars = 6000,
                    timeoutMs = 25_000,
                    tool = CrawlPageTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "crawl_site",
                    description = "启动站点爬取任务。",
                    parameters = listOf(
                        ToolParam("url", required = true, description = "站点地址"),
                        ToolParam("max_pages", ToolParamType.INTEGER, description = "最大页数，默认 10"),
                    ),
                    maxObservationChars = 4000,
                    timeoutMs = 25_000,
                    tool = CrawlSiteTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "crawl_status",
                    description = "查询站点爬取任务状态。",
                    parameters = listOf(
                        ToolParam("task_id", required = true, description = "任务 id"),
                    ),
                    tool = CrawlStatusTool().asSuspending(),
                ),
            )
            add(
                ToolSpec(
                    name = "submit_review",
                    description = "提交课程评价；只在用户明确要求提交评价时使用。",
                    parameters = listOf(
                        ToolParam("course_code", required = true, description = "课程代码"),
                        ToolParam("content", required = true, description = "评价正文"),
                        ToolParam(
                            "review_type",
                            enumValues = listOf("lecturer", "course", "course_teacher", "section", "new_course"),
                            description = "评价类型",
                        ),
                        ToolParam("lecturer_name", description = "教师姓名"),
                        ToolParam("course_name", description = "课程名"),
                        ToolParam("teacher_name", description = "教师姓名"),
                        ToolParam("title", description = "章节/标题"),
                        ToolParam("author_name", description = "作者名，默认匿名"),
                    ),
                    timeoutMs = 30_000,
                    tool = SubmitReviewTool().asSuspending(),
                ),
            )
        }

        private fun ReActToolRegistry.add(spec: ToolSpec) {
            register(spec)
        }
    }
}
