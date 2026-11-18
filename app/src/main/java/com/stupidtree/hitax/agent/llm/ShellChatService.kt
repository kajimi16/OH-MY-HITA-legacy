package com.stupidtree.hitax.agent.llm

import android.app.Application
import com.stupidtree.hita.api.PluginToolHandler
import com.stupidtree.hita.api.PluginToolSpec
import com.stupidtree.hitax.HApplication
import com.stupidtree.hitax.agent.core.AgentTraceEvent
import com.stupidtree.hitax.market.PluginTrustStore
import com.stupidtree.hitax.plugin.PluginCatalog
import com.stupidtree.hitax.agent.tools.ReActToolInput
import com.stupidtree.hitax.agent.tools.ReActToolRegistry
import com.stupidtree.hitax.agent.tools.SuspendingReActTool
import com.stupidtree.hitax.agent.tools.ToolParam
import com.stupidtree.hitax.agent.tools.ToolParamType
import com.stupidtree.hitax.agent.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 宿主版 ReAct：只依赖已移植的工具注册表，不依赖上游 EASRepository。 */
object ShellChatService {
    private const val MAX_STEPS = 6

    private fun registry(application: Application): ReActToolRegistry {
        val registry = ReActToolRegistry.createDefault()
        val host = (application as? HApplication)?.pluginHost ?: return registry
        val trustStore = PluginTrustStore(application)
        host.pluginTools()
            .filter { binding ->
                // 内置插件随 APK 发布；第三方 dex 插件必须过 verified 白名单。
                PluginCatalog.entry(application, binding.pluginId) != null ||
                    trustStore.isVerified(binding.pluginId)
            }
            .forEach { binding ->
                val spec = binding.spec.toToolSpec(binding.handler) ?: return@forEach
                // 插件不能悄悄覆盖宿主内置工具。
                if (registry.spec(spec.name) == null) registry.register(spec)
            }
        return registry
    }

    private fun PluginToolSpec.toToolSpec(handler: PluginToolHandler): ToolSpec? {
        if (name.isBlank() || description.isBlank()) return null
        val params = parameters.map { param ->
            val type = when (param.type.lowercase()) {
                "integer" -> ToolParamType.INTEGER
                "number" -> ToolParamType.NUMBER
                "boolean" -> ToolParamType.BOOLEAN
                else -> ToolParamType.STRING
            }
            ToolParam(
                name = param.name,
                type = type,
                required = param.required,
                description = param.description,
                aliases = param.aliases,
                enumValues = param.enumValues,
            )
        }
        return ToolSpec(
            name = name,
            description = description,
            parameters = params,
            maxObservationChars = maxObservationChars.coerceIn(1, 20_000),
            timeoutMs = timeoutMs.coerceIn(1, 120_000),
            tool = SuspendingReActTool { input -> handler.execute(input.actionInput) },
        )
    }

    suspend fun chat(
        history: List<ChatMessage>,
        application: Application,
        timetableId: String?,
        onTrace: (AgentTraceEvent) -> Unit = {},
    ): LlmChatResult = withContext(Dispatchers.IO) {
        val userMessage = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (userMessage.isBlank()) return@withContext LlmChatResult.Error("消息为空")
        val toolRegistry = registry(application)
        val messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt(toolRegistry)))
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
            val toolInput = ReActToolInput(
                actionInput = parsed.actionInput,
                userMessage = userMessage,
                application = application,
                timetableId = timetableId,
                onTrace = onTrace,
            )
            val observation = toolRegistry.execute(parsed.action, toolInput, onTrace).text
            observations += "【${parsed.action}】\n$observation"
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

    private fun systemPrompt(toolRegistry: ReActToolRegistry): String = buildString {
        append("你是 HITA 课表助手。按 ReAct 格式回答。\n")
        append("格式：\n思考：...\n动作：工具名\n动作输入：<JSON>\n")
        append("或者：\n答案：...\n\n")
        append("工具目录：\n")
        append(toolRegistry.catalog())
        append("\n\n动作输入必须是合法 JSON，不要输出 Markdown 代码块。")
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
