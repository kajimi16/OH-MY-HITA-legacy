package com.stupidtree.hitax.agent.tools

import com.stupidtree.hitax.agent.remote.AgentBackendClient

class CrawlStatusTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        return try {
            val taskId = Regex(""""task_id"\s*:\s*"([^"]+)"""").find(input.actionInput)?.groupValues?.get(1)
                ?: input.actionInput.trim()
            AgentBackendClient.crawlStatusSync(taskId) ?: "无法获取爬取状态"
        } catch (e: Exception) {
            "获取爬取状态失败：${e.message ?: "未知错误"}"
        }
    }
}
