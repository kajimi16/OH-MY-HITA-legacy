package com.stupidtree.hitax.agent

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.stupidtree.hitax.agent.data.ChatSessionRepository
import com.stupidtree.hitax.agent.llm.ChatMessage
import com.stupidtree.hitax.agent.llm.LlmChatResult
import com.stupidtree.hitax.agent.llm.ShellChatService
import com.stupidtree.hitax.agent.model.ChatMessageRecord
import com.stupidtree.hitax.agent.model.ChatSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AgentMessage(
    val role: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

class AgentChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatSessionRepository.get(application)
    val messages = mutableStateListOf<AgentMessage>()
    val sessions: LiveData<List<ChatSession>> get() = repository.sessions
    var currentSessionId by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private var initJob: Job? = null

    init {
        initJob = viewModelScope.launch { ensureSession() }
    }

    private suspend fun ensureSession(): String {
        currentSessionId?.let { return it }
        val latest = repository.latest().await()
        val session = latest ?: ChatSession().also {
            repository.create(it).await()
            currentSessionId = it.id
            return it.id
        }
        currentSessionId = session.id
        restore(session.id)
        return session.id
    }

    private suspend fun restore(sessionId: String) {
        val records = repository.restore(sessionId).await()
        messages.clear()
        records.filter { it.role != ChatMessageRecord.Role.TRACE }.forEach {
            messages += AgentMessage(it.role.name.lowercase(), it.text, it.timestampMs)
        }
    }

    fun send(text: String, timetableId: String?) {
        val content = text.trim()
        if (content.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val sessionId = ensureSession()
            append(sessionId, ChatMessageRecord.Role.USER, content)
            messages += AgentMessage("user", content)
            val history = messages
                .filter { it.role == "user" || it.role == "assistant" }
                .map { ChatMessage(role = it.role, content = it.text) }
            when (val result = ShellChatService.chat(history, getApplication(), timetableId)) {
                is LlmChatResult.Success -> {
                    append(sessionId, ChatMessageRecord.Role.ASSISTANT, result.text)
                    messages += AgentMessage("assistant", result.text)
                }
                is LlmChatResult.Error -> {
                    val error = "出错了：${result.error}"
                    append(sessionId, ChatMessageRecord.Role.ASSISTANT, error)
                    messages += AgentMessage("assistant", error)
                }
            }
            busy = false
        }
    }

    private suspend fun append(sessionId: String, role: ChatMessageRecord.Role, text: String) {
        repository.append(sessionId, ChatMessageRecord(role = role, text = text)).await()
    }

    /** 会话列表切换；发送中不切换，避免回复写错会话。 */
    fun switchToSession(sessionId: String) {
        if (busy || sessionId == currentSessionId) return
        viewModelScope.launch {
            currentSessionId = sessionId
            restore(sessionId)
        }
    }

    fun createNewSession() {
        if (busy) return
        viewModelScope.launch {
            val session = ChatSession()
            repository.create(session).await()
            currentSessionId = session.id
            messages.clear()
        }
    }

    fun deleteSession(session: ChatSession) {
        if (busy) return
        viewModelScope.launch {
            val latest = repository.delete(session).await()
            if (currentSessionId == session.id) {
                if (latest == null) createNewSession() else {
                    currentSessionId = latest.id
                    restore(latest.id)
                }
            }
        }
    }
}
