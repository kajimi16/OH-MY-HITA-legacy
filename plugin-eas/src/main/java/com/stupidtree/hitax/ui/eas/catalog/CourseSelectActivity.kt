package com.stupidtree.hitax.ui.eas.catalog

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.source.preference.EasPreferenceSource
import com.stupidtree.hitax.data.source.web.eas.EASource

/**
 * 选课入口（移植 HITA_Android 3.1.2 的 ShenzhenCourseCatalog 最小能力集）：
 * 可选课程列表 + 点击加入购物车。
 *
 * 暂未包含上游的选课池切换、分页、关键词、草稿与周视图预览，这些留到第二阶段。
 */
class CourseSelectActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val source = EASource()
    private var term: TermItem? = null
    private var subjects: List<TermSubject> = emptyList()
    private val labels = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView = ListView(this)
        setContentView(listView)

        val token = EasPreferenceSource.getInstance(applicationContext).getEasToken()
        if (!token.isLogin()) {
            toast("请先登录教务")
            finish()
            return
        }

        EASRepository.getInstance(application).getAllTerms().observe(this) { state ->
            if (state.state != DataState.STATE.SUCCESS) {
                toast(if (state.state == DataState.STATE.NOT_LOGGED_IN) "请先登录教务" else "学期加载失败")
                return@observe
            }
            term = state.data?.firstOrNull { it.isCurrent } ?: state.data?.firstOrNull()
            loadCourses()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val subject = subjects.getOrNull(position) ?: return@setOnItemClickListener
            val currentTerm = term ?: return@setOnItemClickListener
            source.selectCourse(token, currentTerm, subject.key ?: return@setOnItemClickListener)
                .observe(this) { result ->
                    toast(if (result.state == DataState.STATE.SUCCESS) "已加入购物车：${subject.name}" else "选课失败：${result.message}")
                }
        }
    }

    private fun loadCourses() {
        val token = EasPreferenceSource.getInstance(applicationContext).getEasToken()
        val currentTerm = term ?: return
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("加载中…"))
        source.getAvailableCourses(token, currentTerm).observe(this) { state ->
            if (state.state != DataState.STATE.SUCCESS) {
                listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("加载失败：${state.message}"))
                return@observe
            }
            subjects = state.data.orEmpty()
            labels.clear()
            subjects.forEach { labels += "${it.name}（${it.code}）· ${it.credit} 学分" }
            if (labels.isEmpty()) labels += "本学期暂无可选课程"
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
