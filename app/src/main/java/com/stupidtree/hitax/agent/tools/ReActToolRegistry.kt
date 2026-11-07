package com.stupidtree.hitax.agent.tools

/**
 * 工具注册表的最小可运行实现。具体本地/远程工具按上游逐个移植后注册到这里。
 */
class ReActToolRegistry {
    private val tools = mutableMapOf<String, SuspendingReActTool>()

    fun register(name: String, tool: ReActTool) {
        register(name, SuspendingReActTool { input -> tool.execute(input) })
    }

    fun register(name: String, tool: SuspendingReActTool) {
        tools[normalizeName(name)] = tool
    }

    fun get(name: String): SuspendingReActTool? = tools[normalizeName(name)]

    fun names(): List<String> = tools.keys.toList()

    companion object {
        fun normalizeName(name: String): String {
            return name.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
        }

        fun createDefault(): ReActToolRegistry = ReActToolRegistry().apply {
            register("get_timetable", GetTimetableTool())
            register("add_activity", AddActivityTool())
            register("search_empty_classroom", SearchEmptyClassroomTool())
            register("search_teacher", SearchTeacherTool())
            register("search_external_resource", SearchExternalResourceTool())
            register("search_timetable", SearchTimetableTool())
            register("search_course", SearchCourseTool())
            register("get_course_detail", GetCourseDetailTool())
            register("web_search", WebSearchTool())
            register("rag_search", RagSearchTool())
            register("crawl_page", CrawlPageTool())
            register("crawl_site", CrawlSiteTool())
            register("crawl_status", CrawlStatusTool())
            register("submit_review", SubmitReviewTool())
        }
    }
}
