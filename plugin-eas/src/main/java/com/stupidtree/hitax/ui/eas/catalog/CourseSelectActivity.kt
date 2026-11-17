package com.stupidtree.hitax.ui.eas.catalog

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.source.preference.EasPreferenceSource
import com.stupidtree.hitax.data.source.web.eas.EASource
import com.stupidtree.hitax.eas.selectable.CourseMeetings
import com.stupidtree.hitax.eas.selectable.CoursePlanPreview
import com.stupidtree.hitax.eas.selectable.SelectableCourse

/**
 * 选课页（移植 HITA_Android 3.1.2 ShenzhenCourseCatalog 的前两步）：
 * 选课池切换 + 关键词检索 + 分页加载 + 加入购物车。
 *
 * 草稿/冲突提示与周视图预览留到后续里程碑。
 */
class CourseSelectActivity : AppCompatActivity() {

    private data class Pool(val code: String, val name: String)

    private val pools = listOf(
        Pool("bx-b-b", "必修课程池"),
        Pool("xx-b-b", "限选课程池"),
        Pool("cxcytx-b-b", "创新创业通选课"),
        Pool("shsj-b-b", "社会实践课"),
        Pool("jsrw-b-b", "竞赛指导类课程"),
        Pool("cxyx-b-b", "创新研修"),
        Pool("cxsy-b-b", "创新实验"),
        Pool("cx-b-b", "重修"),
        Pool("buxiu-bcyxfj-b-b", "补修"),
        Pool("sx-b-b", "跨专业课程体系"),
        Pool("tsk-b-b", "文理通识"),
        Pool("mooc-b-b", "MOOC"),
    )

    private val source = EASource()
    private var term: TermItem? = null
    private var page = 1
    private var keyword = ""
    private var subjects: MutableList<SelectableCourse> = mutableListOf()
    private val picked = mutableListOf<SelectableCourse>()
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var poolSpinner: Spinner
    private lateinit var keywordInput: EditText
    private lateinit var moreButton: Button
    private lateinit var draftBar: Button
    private lateinit var previewButton: Button

    private val labels = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!token().isLogin()) {
            toast("请先登录教务"); finish(); return
        }

        poolSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@CourseSelectActivity, android.R.layout.simple_spinner_item, pools.map { it.name })
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = reload()
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
            })
        }
        keywordInput = EditText(this).apply {
            hint = "课程名称/代码"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val searchButton = Button(this).apply {
            text = "搜索"
            setOnClickListener { keyword = keywordInput.text.toString().trim(); reload() }
        }
        moreButton = Button(this).apply {
            text = "加载更多"
            setOnClickListener { load(page + 1) }
        }
        draftBar = Button(this).apply {
            setOnClickListener { clearDraft() }
        }
        previewButton = Button(this).apply {
            text = "预览已选课程"
            setOnClickListener { showPreview() }
        }
        listView = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        listView.adapter = adapter

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(poolSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
            addView(keywordInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
            addView(searchButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(draftBar)
            addView(previewButton)
            addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(moreButton)
        }
        setContentView(root)

        listView.setOnItemClickListener { _, _, position, _ ->
            val course = subjects.getOrNull(position) ?: return@setOnItemClickListener
            val subject = course.subject
            val currentTerm = term ?: return@setOnItemClickListener
            val courseId = subject.key ?: return@setOnItemClickListener
            val conflict = CourseMeetings.conflict(course, picked)
            if (conflict != null) {
                toast(conflict)
                return@setOnItemClickListener
            }
            picked += course
            // 先记草稿（离线可见），再调用教务加购物车
            CourseSelectionDraft.toggle(
                this,
                currentTerm.getCode(),
                DraftCourse(courseId, subject.name, subject.code.orEmpty(), subject.credit),
            )
            refreshDraftBar()
            source.selectCourse(token(), currentTerm, courseId, pools[poolSpinner.selectedItemPosition].code)
                .observe(this) { result ->
                    toast(if (result.state == DataState.STATE.SUCCESS) "已加入购物车：${subject.name}" else "选课失败：${result.message}")
                }
        }

        refreshDraftBar()
        EASRepository.getInstance(application).getAllTerms().observe(this) { state ->
            if (state.state != DataState.STATE.SUCCESS) {
                toast(if (state.state == DataState.STATE.NOT_LOGGED_IN) "请先登录教务" else "学期加载失败")
                return@observe
            }
            term = state.data?.firstOrNull { it.isCurrent } ?: state.data?.firstOrNull()
            reload()
        }
    }

    private fun token() = EasPreferenceSource.getInstance(applicationContext).getEasToken()

    private fun reload() {
        subjects = mutableListOf()
        labels.clear()
        adapter.notifyDataSetChanged()
        load(1)
    }

    private fun load(targetPage: Int) {
        val currentTerm = term ?: return
        val pool = pools.getOrNull(poolSpinner.selectedItemPosition) ?: pools.first()
        if (targetPage == 1 && labels.isEmpty()) {
            labels += "加载中…"
            adapter.notifyDataSetChanged()
        }
        source.getSelectableCourses(token(), currentTerm, pool.code, targetPage, 20, keyword)
            .observe(this) { state ->
                if (targetPage == 1 && labels.firstOrNull() == "加载中…") {
                    labels.clear()
                }
                if (state.state != DataState.STATE.SUCCESS) {
                    if (targetPage == 1) {
                        labels.clear()
                        labels += "加载失败：${state.message}"
                        adapter.notifyDataSetChanged()
                    } else {
                        toast("加载失败：${state.message}")
                    }
                    return@observe
                }
                val items = state.data.orEmpty()
                page = targetPage
                subjects.addAll(items)
                items.forEach {
                    val meet = it.meetings.firstOrNull()
                    val where = if (meet != null) " · 周${CourseMeetings.weekdayName(meet.weekday)}第${meet.beginPeriod}-${meet.endPeriod}节" else ""
                    labels += "${it.subject.name}（${it.subject.code}）· ${it.subject.credit} 学分$where"
                }
                if (labels.isEmpty()) labels += "【${pool.name}】暂无可选课程"
                adapter.notifyDataSetChanged()
                moreButton.isEnabled = items.size >= 20
            }
    }

    private fun refreshDraftBar() {
        val term = term ?: return
        val courses = CourseSelectionDraft.courses(this, term.getCode())
        draftBar.text = if (courses.isEmpty()) "已选 0 门（点课程加入/移除）" else "已选 ${courses.size} 门：${courses.joinToString("、") { it.name }}（点击清空）"
    }

    private fun clearDraft() {
        val term = term ?: return
        CourseSelectionDraft.clear(this, term.getCode())
        refreshDraftBar()
        toast("已清空选课草稿")
    }

    /** 周预览：按上游 eventsForWeek 规则列出所选周的课程事件；无法投影的课程单独提示。 */
    private fun showPreview() {
        if (picked.isEmpty()) {
            toast("还没有已选课程")
            return
        }
        val periodCount = 12
        val maxWeek = CoursePlanPreview.maxWeek(picked)
        val week = maxWeek.coerceAtMost(1)
        val events = CoursePlanPreview.eventsForWeek(picked, week, periodCount)
        val incomplete = CoursePlanPreview.incomplete(picked, periodCount)
        val lines = mutableListOf<String>()
        events.forEach { e ->
            val place = if (e.location.isNotBlank()) " · ${e.location}" else ""
            lines += "周${CourseMeetings.weekdayName(e.weekday)} 第${e.beginPeriod}-${e.endPeriod}节 · ${e.name}$place"
        }
        if (incomplete.isNotEmpty()) {
            lines += "无法投影：" + incomplete.joinToString("、") { it.subject.name }
        }
        if (lines.isEmpty()) lines += "第${week}周没有排课"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选课预览（第${week}周 / 共${maxWeek}周）")
            .setItems(lines.toTypedArray(), null)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
