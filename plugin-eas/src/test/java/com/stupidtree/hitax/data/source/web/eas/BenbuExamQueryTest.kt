package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.ExamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class BenbuExamQueryTest {

    private fun exam(name: String) = ExamItem().apply {
        courseName = name
        examDate = "2026-10-31"
        examType = "期末"
    }

    @Test
    fun queriesFinalMidtermAndResitInOrder() {
        val requested = mutableListOf<String>()
        val result = BenbuExamQuery.load { code, label ->
            requested += "$code:$label"
            listOf(exam(label))
        }

        assertEquals(listOf("01:期末", "02:期中", "03:补考"), requested)
        assertEquals(DataState.STATE.SUCCESS, result.state)
        assertEquals(listOf("期末", "期中", "补考"), result.data!!.map { it.courseName })
        assertEquals(null, result.message)
    }

    @Test
    fun oneFailedBatchKeepsTheOthersAndReportsIt() {
        val result = BenbuExamQuery.load { _, label ->
            if (label == "补考") throw IllegalStateException("空表")
            listOf(exam(label))
        }

        assertEquals(DataState.STATE.SUCCESS, result.state)
        assertEquals(listOf("期末", "期中"), result.data!!.map { it.courseName })
        assertTrue(result.message!!.contains("补考"))
    }

    @Test
    fun allBatchesFailingGivesFetchFailed() {
        val result = BenbuExamQuery.load { _, _ -> throw IllegalStateException("坏页") }

        assertEquals(DataState.STATE.FETCH_FAILED, result.state)
        assertTrue(result.data!!.isEmpty())
        assertTrue(result.message!!.contains("期末"))
    }

    @Test
    fun sessionExpiryStopsFurtherQueriesButKeepsFetchedExams() {
        var calls = 0
        val result = BenbuExamQuery.load { _, label ->
            calls++
            if (label == "期中") throw ExamSessionExpiredException()
            listOf(exam(label))
        }

        assertEquals(DataState.STATE.NOT_LOGGED_IN, result.state)
        assertEquals(1, result.data!!.size)
        assertEquals("登录已失效，请重新登录", result.message)
        // 登录失效后不再继续查补考
        assertEquals(2, calls)
    }

    @Test
    fun cancellableEntryPointPropagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            BenbuExamQuery.loadCancellable({}) { _, label ->
                if (label == "期中") throw CancellationException("取消")
                listOf(exam(label))
            }
        }
    }

    @Test
    fun cancellableEntryPointHonoursCheckCancelled() {
        assertThrows(CancellationException::class.java) {
            BenbuExamQuery.loadCancellable({ throw CancellationException("取消") }) { _, label ->
                listOf(exam(label))
            }
        }
    }
}