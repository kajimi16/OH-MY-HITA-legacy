package com.stupidtree.hitax.agent.llm

import android.app.Application
import com.stupidtree.hitax.agent.core.AgentTraceEvent
import com.stupidtree.hitax.agent.tools.ReActToolInput
import com.stupidtree.hitax.agent.tools.ReActToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 宿主版 ReAct：只依赖已移植的工具注册表，不依赖上游 EASRepository。 */
object ShellChatService {
    private val toolRegistry = ReActToolRegistry.createDefault()
    private const val MAX_STEPS = 6

    suspend fun chat(
        history: List<ChatMessage>,
        application: Application,
        timetableId: String?,
        onTrace: (AgentTraceEvent) -> Unit = {},
    ): LlmChatResult = withContext(Dispatchers.IO) {
        val userMessage = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (userMessage.isBlank()) return@withContext LlmChatResult.Error("消息为空")
        val messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt()))
        messages += history
        val observations = mutableListOf<String>()

        repeat(MAX_STEPS) { step ->
            onTrace(AgentTraceEvent(stage = "react_step", message = "步骤 ${step + 1}/$MAX_STEPS", payload = ""))
            val response = runCatching {
                LlmClient.chatCompletion(
                    application,
                    ChatCompletionRequest(model = LlmClient.MODEL, messages = messages),
                )
            }.getOrElse { return@withContext LlmChatResult.Error(it.message ?: "网络错误") }
            if (!response.isSuccessful) {
                return@withContext LlmChatResult.Error("LLM HTTP ${response.code()}")
            }
            val content = response.body()?.choices?.firstOrNull()?.message?.content
                ?: return@withContext LlmChatResult.Error("LLM 返回为空")
            val parsed = parseStep(content)
            if (parsed.action == "答案" || parsed.action.isBlank()) {
                val answer = parsed.thought.ifBlank { content }.trim()
                return@withContext LlmChatResult.Success(answer, observations.joinToString("\n"))
            }
            val tool = toolRegistry.get(parsed.action)
            val observation = if (tool == null) {
                "未知工具：${parsed.action}"
            } else {
                runCatching {
                    tool.execute(
                        ReActToolInput(
                            actionInput = parsed.actionInput,
                            userMessage = userMessage,
                            application = application,
                            timetableId = timetableId,
                            onTrace = onTrace,
                        ),
                    )
                }.getOrElse { "工具执行失败：${it.message}" }.orEmpty()
            }
            observations += "【${parsed.action}】\n${observation.take(1800)}"
            onTrace(AgentTraceEvent(stage = "observation", message = "观察：${observation.take(80)}", payload = observation.take(500)))
            messages += ChatMessage(role = "assistant", content = content)
            messages += ChatMessage(role = "user", content = "观察结果：$observation")
        }
        LlmChatResult.Success(
            buildString {
                append("达到最大步骤限制。已获得的信息：\n\n")
                append(observations.takeLast(4).joinToString("\n\n"))
            }.trim(),
            observations.joinToString("\n"),
        )
    }

    private fun systemPrompt(): String = buildString {
        append("你是 HITA 课表助手。按 ReAct 格式回答。\n")
        append("格式：\n思考：...\n动作：工具名\n动作输入：<JSON>\n")
        append("或者：\n答案：...\n\n")
        append("工具目录：\n")
        append("- get_timetable：动作输入 {\"day_offset\":0}，0=今天，1=明天，-1=昨天。\n")
        append("- add_activity：动作输入 {\"name\":\"活动名\",\"day_offset\":0,\"start_time\":\"15:00\",\"end_time\":\"16:00\",\"place\":\"地点\"}。\n")
        append("- search_empty_classroom：动作输入 {\"week\":1,\"building\":\"\",\"day\":1}；day 可省略。\n")
        append("- search_teacher：动作输入 {\"name\":\"教师姓名\"}。\n\n")
        append("动作输入必须是合法 JSON，不要输出 Markdown 代码块。")
        append("不要编造课表、空教室或教师数据；不知道就说明。")
    }

    internal data class ParsedStep(val thought: String, val action: String, val actionInput: String)

    internal fun parseStep(text: String): ParsedStep {
        val cleaned = text.replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "").trim()
        val thought = Regex("(?is)思考[：:]\\s*(.+?)(?=\\n动作[：:]|\\n答案[：:]|$)")
            .find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val action = Regex("(?im)^[ \\t]*动作[：:]\\s*(\\S+)")
            .find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val actionInput = Regex("(?is)动作输入[：:]\\s*(.+?)(?=\\n思考[：:]|\\n观察[：:]|$)")
            .find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val answer = Regex("(?is)答案[：:]\\s*(.+)")
            .find(cleaned)?.groupValues?.get(1)?.trim()
        if (action.isBlank() && answer != null) return ParsedStep(answer, "答案", "")
        if (thought.isBlank() && action.isBlank()) return ParsedStep(cleaned, "答案", "")
        return ParsedStep(thought, action, actionInput)
    }
}

sealed class LlmChatResult {
    data class Success(val text: String, val thinking: String? = null) : LlmChatResult()
    data class Error(val error: String) : LlmChatResult()
}
