package com.stupidtree.hitax.agent

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stupidtree.hitax.agent.llm.ChatMessage
import com.stupidtree.hitax.agent.llm.LlmChatResult
import com.stupidtree.hitax.agent.llm.ShellChatService
import kotlinx.coroutines.launch

data class AgentMessage(val role: String, val text: String)

class AgentChatViewModel(application: Application) : AndroidViewModel(application) {
    val messages = mutableStateListOf<AgentMessage>()
    var busy by mutableStateOf(false)
        private set

    fun send(text: String, timetableId: String?) {
        val content = text.trim()
        if (content.isEmpty() || busy) return
        messages += AgentMessage("user", content)
        busy = true
        viewModelScope.launch {
            val history = messages
                .filter { it.role == "user" || it.role == "assistant" }
                .map { ChatMessage(role = it.role, content = it.text) }
            when (val result = ShellChatService.chat(history, getApplication(), timetableId)) {
                is LlmChatResult.Success -> messages += AgentMessage("assistant", result.text)
                is LlmChatResult.Error -> messages += AgentMessage("assistant", "出错了：${result.error}")
            }
            busy = false
        }
    }
}
