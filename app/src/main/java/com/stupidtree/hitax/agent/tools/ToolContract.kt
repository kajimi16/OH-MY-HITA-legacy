package com.stupidtree.hitax.agent.tools

import com.stupidtree.hitax.agent.core.AgentTraceEvent

enum class ToolParamType { STRING, INTEGER, NUMBER, BOOLEAN }

/** 同步工具适配到统一的可挂起执行接口。 */
fun ReActTool.asSuspending(): SuspendingReActTool = SuspendingReActTool { input -> execute(input) }

/**
 * 一个模型可见参数的声明。schema 同时用于生成工具目录与执行前校验。
 * [aliases] 让旧工具已经接受的同义字段继续通过校验。
 */
data class ToolParam(
    val name: String,
    val type: ToolParamType = ToolParamType.STRING,
    val required: Boolean = false,
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val enumValues: List<String> = emptyList(),
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: List<ToolParam> = emptyList(),
    val maxObservationChars: Int = 1800,
    /** 协作式超时：同步工具若不响应线程中断，仍可能继续运行。 */
    val timeoutMs: Long = 15_000,
    val tool: SuspendingReActTool,
)

data class ToolCall(
    val name: String,
    val actionInput: String,
)

data class ToolExecution(
    val text: String,
    val error: Boolean = false,
    val truncated: Boolean = false,
)

sealed class ToolDecision {
    data object Allow : ToolDecision()

    data class Deny(val reason: String) : ToolDecision()

    /** 需要人类确认；在当前宿主里按拒绝处理，留给未来的审批 UI。 */
    data class Ask(val reason: String) : ToolDecision()
}

fun interface ToolGuard {
    fun check(call: ToolCall): ToolDecision
}

fun interface ToolPostProcessor {
    fun process(call: ToolCall, result: ToolExecution): ToolExecution?
}
