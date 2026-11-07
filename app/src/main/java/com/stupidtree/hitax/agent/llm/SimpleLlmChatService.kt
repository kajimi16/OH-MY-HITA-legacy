package com.stupidtree.hitax.agent.llm

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 无工具的最小聊天服务：先保证 Agent UI 可对话，工具随后按上游注册。 */
object SimpleLlmChatService {
    suspend fun chat(
        history: List<ChatMessage>,
        application: Application,
        systemPrompt: String = "你是 HITA 课表助手。回答简洁、准确，不编造课表数据。",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = ChatCompletionRequest(
                model = LlmClient.MODEL,
                messages = buildList {
                    add(ChatMessage(role = "system", content = systemPrompt))
                    addAll(history)
                },
            )
            val response = LlmClient.chatCompletion(application, request)
            if (!response.isSuccessful) {
                error("LLM HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}")
            }
            response.body()?.choices?.firstOrNull()?.message?.content
                ?.takeIf { it.isNotBlank() }
                ?: error("LLM 返回为空")
        }
    }
}
