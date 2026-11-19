package com.stupidtree.hita.api

import androidx.lifecycle.LiveData
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.TermSubject

/**
 * 深圳教务课程池（API 7，增量契约）。
 * 只读课程列表；选课/附件/历史挂科率仍由后续版本扩展。
 */
interface CourseCatalogService {
    fun terms(): LiveData<DataState<List<TermItem>>>

    fun courses(
        term: TermItem,
        poolCode: String = "bx-b-b",
        page: Int = 1,
        pageSize: Int = 20,
    ): LiveData<DataState<List<TermSubject>>>

    companion object {
        val KEY = ServiceKey<CourseCatalogService>("hita.eas.courseCatalog")
    }
}
