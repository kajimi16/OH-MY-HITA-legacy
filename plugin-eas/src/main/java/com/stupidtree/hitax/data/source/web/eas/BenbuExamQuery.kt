package com.stupidtree.hitax.data.source.web.eas

import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.ExamItem
import kotlinx.coroutines.CancellationException

/** 本部考试查询中途登录失效。 */
internal class ExamSessionExpiredException : IllegalStateException()

/**
 * 本部（哈尔滨）考试查询的三个批次编排。
 *
 * 逐条复刻 HITA_Android v3.1.2 `BenbuExamQuery`：
 * 依次查期末（01）、期中（02）、补考（03），**某一批失败不清空已拿到的批次**——
 * 补考页常年是空表甚至坏页，不能因此把期中/期末一起丢掉。
 *
 * 结果状态：
 * - 登录失效 → [DataState.STATE.NOT_LOGGED_IN]，并保留已拿到的考试；
 * - 有批次失败但有数据 → SUCCESS，message 列出失败批次；
 * - 全部失败且无数据 → FETCH_FAILED。
 */
internal object BenbuExamQuery {

    /** 每批的请求参数：批次码与展示用标签。 */
    val BATCHES: List<Pair<String, String>> = listOf(
        "01" to "期末",
        "02" to "期中",
        "03" to "补考",
    )

    fun load(fetch: (String, String) -> List<ExamItem>): DataState<List<ExamItem>> =
        loadInternal(fetch, checkCancelled = null)

    fun loadCancellable(checkCancelled: () -> Unit, fetch: (String, String) -> List<ExamItem>): DataState<List<ExamItem>> =
        loadInternal(fetch, checkCancelled)

    private fun loadInternal(
        fetch: (String, String) -> List<ExamItem>,
        checkCancelled: (() -> Unit)?,
    ): DataState<List<ExamItem>> {
        val exams = mutableListOf<ExamItem>()
        val failures = mutableListOf<String>()

        for ((code, label) in BATCHES) {
            checkCancelled?.invoke()
            try {
                val batch = fetch(code, label)
                checkCancelled?.invoke()
                exams.addAll(batch)
            } catch (_: ExamSessionExpiredException) {
                return DataState<List<ExamItem>>(exams.toList(), DataState.STATE.NOT_LOGGED_IN).apply {
                    message = "登录已失效，请重新登录"
                }
            } catch (cancelled: CancellationException) {
                // LiveData 入口历史上把"取消某一批"当作失败处理，只有可取消入口才继续抛出
                if (checkCancelled != null) throw cancelled
                failures += label
            } catch (_: Exception) {
                failures += label
            }
        }

        val state = if (failures.isNotEmpty() && exams.isEmpty()) {
            DataState.STATE.FETCH_FAILED
        } else {
            DataState.STATE.SUCCESS
        }
        return DataState<List<ExamItem>>(exams.toList(), state).apply {
            if (failures.isNotEmpty()) message = failures.joinToString("、") + "考试查询失败，请刷新重试"
        }
    }
}