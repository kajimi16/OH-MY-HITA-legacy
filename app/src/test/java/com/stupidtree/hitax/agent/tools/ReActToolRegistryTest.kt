package com.stupidtree.hitax.agent.tools

import org.junit.Assert.assertEquals
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
    fun codeFenceIsStrippedBeforeValidation() {
        val wrapped = "```json\n{\"name\":\"张老师\"}\n```"
        assertTrue(validate("search_teacher", wrapped).isEmpty())
    }
}
