package com.stupidtree.hitax.agent.tools

import android.app.Application
import com.stupidtree.hitax.agent.core.AgentTraceEvent
import com.stupidtree.hitax.agent.model.AgentResourceCard

data class ReActToolInput(
    val actionInput: String,
    val userMessage: String,
    val application: Application,
    val timetableId: String?,
    val onTrace: (AgentTraceEvent) -> Unit,
    val onResourceCards: (List<AgentResourceCard>) -> Unit = {},
)

fun interface ReActTool {
    fun execute(input: ReActToolInput): String?
}

fun interface SuspendingReActTool {
    suspend fun execute(input: ReActToolInput): String?
}
