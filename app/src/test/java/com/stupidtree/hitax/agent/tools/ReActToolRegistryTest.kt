package com.stupidtree.hitax.agent.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReActToolRegistryTest {

    private val registry = ReActToolRegistry.createDefault()

    private fun validate(name: String, input: String): List<String> =
        registry.validate(registry.spec(name)!!, input)

    @Test
    fun catalogContainsEveryRegisteredToolAndRequiredFields() {
        val lines = registry.catalog().lines().filter { it.startsWith("- ") }

        assertEquals(14, lines.size)
        assertTrue(lines.any { it.contains("submit_review") && it.contains("必填：course_code,content") })
        assertTrue(lines.any { it.contains("get_course_detail") && it.contains("course_code") })
        assertTrue(lines.any { it.contains("web_search") && it.contains("param") })
        assertTrue(lines.any { it.contains("day_offset=相对今天的天数") })
        assertTrue(lines.any { it.contains("week=周次") })
    }

    @Test
    fun requiredFieldIsEnforcedForJsonArguments() {
        assertEquals(listOf("缺少参数 name"), validate("search_teacher", "{}"))
        assertEquals(listOf("缺少参数 course_code", "缺少参数 content"), validate("submit_review", "{}"))
    }

    @Test
    fun aliasesStillSatisfyRequiredFields() {
        assertTrue(validate("search_teacher", """{"teacher_name":"张老师"}""").isEmpty())
        assertTrue(validate("search_timetable", """{"keyword":"高数"}""").isEmpty())
        assertTrue(validate("web_search", """{"query":"kotlin"}""").isEmpty())
    }

    @Test
    fun wrongTypeAndEnumAreRejected() {
        assertEquals(
            listOf("参数 name 应为 string"),
            validate("search_teacher", """{"name":42}"""),
        )
        assertEquals(
            listOf("参数 review_type 必须是 lecturer/course/course_teacher/section/new_course"),
            validate(
                "submit_review",
                """{"course_code":"CS1","content":"ok","review_type":"unknown"}""",
            ),
        )
    }

    @Test
    fun nonJsonLegacyInputIsLeftToTheTool() {
        assertTrue(validate("search_teacher", "张老师").isEmpty())
        assertTrue(validate("get_timetable", "").isEmpty())
    }

    @Test
    fun guardDenySkipsTheToolBody() = runBlocking {
        val registry = ReActToolRegistry()
        registry.register(
            ToolSpec(
                name = "x",
                description = "x",
                tool = SuspendingReActTool { "ok" },
            ),
        )
        registry.guard { ToolDecision.Deny("blocked") }
        var called = false

        val result = registry.runPipeline(
            registry.spec("x")!!,
            ToolCall("x", ""),
        ) {
            called = true
            "ok"
        }

        assertTrue(result.error)
        assertTrue(result.text.contains("blocked"))
        assertFalse(called)
    }

    @Test
    fun postProcessorAndPerToolTruncationRunInOrder() = runBlocking {
        val registry = ReActToolRegistry()
        registry.register(
            ToolSpec(
                name = "x",
                description = "x",
                maxObservationChars = 10,
                tool = SuspendingReActTool { "ok" },
            ),
        )
        registry.postProcess { _, result -> result.copy(text = result.text + "!") }

        val result = registry.runPipeline(
            registry.spec("x")!!,
            ToolCall("x", ""),
        ) { "abcdefghijklm" }

        assertTrue(result.truncated)
        assertTrue(result.text.endsWith("…[truncated]"))
        assertTrue(result.text.startsWith("abcdefghij"))
    }

    @Test
    fun timeoutBecomesAStructuredToolError() = runBlocking {
        val registry = ReActToolRegistry()
        registry.register(
            ToolSpec(
                name = "slow",
                description = "slow",
                timeoutMs = 1,
                tool = SuspendingReActTool { "ok" },
            ),
        )

        val result = registry.runPipeline(
            registry.spec("slow")!!,
            ToolCall("slow", ""),
        ) {
            delay(100)
            "late"
        }

        assertTrue(result.error)
        assertTrue(result.text.contains("超时"))
    }

    @Test
    fun invalidArgumentsPreventToolInvocation() = runBlocking {
        val registry = ReActToolRegistry()
        registry.register(
            ToolSpec(
                name = "need",
                description = "need",
                parameters = listOf(ToolParam("name", required = true)),
                tool = SuspendingReActTool { "ok" },
            ),
        )
        var called = false

        val result = registry.runPipeline(
            registry.spec("need")!!,
            ToolCall("need", "{}"),
        ) {
            called = true
            "ok"
        }

        assertTrue(result.error)
        assertFalse(called)
    }

    @Test
    fun codeFenceIsStrippedBeforeValidation() {
        val wrapped = "```json\n{\"name\":\"张老师\"}\n```"
        assertTrue(validate("search_teacher", wrapped).isEmpty())
    }
}
