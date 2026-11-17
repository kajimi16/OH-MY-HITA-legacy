package com.stupidtree.hitax.ui.eas.exam

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.component.data.MTransformations
import com.stupidtree.component.data.Trigger
import com.stupidtree.hitax.data.model.eas.ExamItem
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.source.local.ExamMemoStore
import com.stupidtree.hitax.ui.eas.EASViewModel

class ExamViewModel(application: Application) : EASViewModel(application){
    /**
     * 仓库区
     */
    private val easRepository = EASRepository.getInstance(application)
    private val memoStore = ExamMemoStore(application)

    /**
     * LiveData区
     */
    private val pageController = MutableLiveData<Trigger>()

    val termsLiveData: LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepository.getAllTerms()
    }

    val selectedTermLiveData: MutableLiveData<TermItem> = MutableLiveData()
    val selectedExamTypeLiveData: MutableLiveData<ExamType> = MutableLiveData()

    init {
        // MTransformations.map 的双源版本要两个源都有值才 emit；
        // 不预设“全部”，考试数据到了也不会合并到页面（表现为空态）。
        selectedExamTypeLiveData.value = ExamType.ALL
    }

        /**
     * 教务真实考试 + 本地备忘录的合并源（页面观察 examInfoLiveData 时才会激活）。
     * MediatorLiveData 是懒的：没有人观察它，addSource 的上游就不会执行。
     */
    private val mergedExams =
        androidx.lifecycle.MediatorLiveData<DataState<List<ExamItem>>>().apply {
            addSource(easRepository.getExamInfo()) { state ->
                val server = state.data
                if (state.state == DataState.STATE.SUCCESS) {
                    value = DataState(
                        (if (server.isNullOrEmpty()) emptyList() else server) + memoStore.load(),
                        DataState.STATE.SUCCESS,
                    )
                }
            }
        }

    val examInfoLiveData: LiveData<DataState<List<ExamItem>>> =
        MTransformations.map(mergedExams) { state ->
            val data = state.data
            if (state.state != DataState.STATE.SUCCESS || data.isNullOrEmpty()) {
                return@map state
            }
            val term = selectedTermLiveData.value
            val type = selectedExamTypeLiveData.value
            val filtered = data.filter { item -> matchTerm(item, term) && matchType(item.examType, type) }
            DataState(filtered, state.state)
        }

    /** 学期/类型切换时重新套一次过滤（单源 map 不会因过滤条件变化而自动重算）。 */
    fun refreshFilter() {
        mergedExams.value = mergedExams.value
    }

    /**
     * 方法区
     */
    fun startRefresh() {
        pageController.value = Trigger.actioning
        mergedExams.value = DataState(memoStore.load(), DataState.STATE.SUCCESS)
    }

    fun addMemo(item: ExamItem) {
        mergedExams.value = DataState(memoStore.add(item) + (mergedExams.value?.data.orEmpty().filter { m -> m.memoId != item.memoId }), DataState.STATE.SUCCESS)
    }

    fun deleteMemo(memoId: String) {
        mergedExams.value = DataState(memoStore.delete(memoId), DataState.STATE.SUCCESS)
    }

    private fun matchTerm(item: ExamItem, term: TermItem?): Boolean {
        if (term == null) return true
        val raw = (item.termName ?: "").trim()
        if (raw.isBlank()) {
            val examDate = (item.examDate ?: "").trim()
            val year = Regex("(\\d{4})").find(examDate)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (year != null) {
                val years = Regex("(\\d{4})").findAll(term.yearCode).mapNotNull { it.value.toIntOrNull() }.toList()
                if (years.isNotEmpty()) {
                    val min = years.minOrNull() ?: year
                    val max = years.maxOrNull() ?: year
                    return year in min..max
                }
            }
            return true
        }
        val compact = raw.replace("\\s".toRegex(), "")
        val targetName = term.name.replace("\\s".toRegex(), "")
        if (compact == targetName) return true
        val code = term.getCode().replace("\\s".toRegex(), "")
        if (compact == code) return true
        val yearName = term.yearName.replace("\\s".toRegex(), "")
        val termName = term.termName.replace("\\s".toRegex(), "")
        return (yearName.isNotBlank() && compact.contains(yearName) && compact.contains(termName))
    }

    private fun matchType(examType: String?, type: ExamType?): Boolean {
        if (type == null || type == ExamType.ALL) return true
        val raw = (examType ?: "").trim()
        if (raw.isBlank()) return true
        return when (type) {
            ExamType.MIDTERM -> raw.contains("期中")
            ExamType.FINAL -> raw.contains("期末")
            ExamType.ALL -> true
        }
    }

    enum class ExamType {
        ALL,
        MIDTERM,
        FINAL
    }

}
