package com.stupidtree.hitax.shell

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import com.stupidtree.hita.api.TimetableService
import com.stupidtree.hitax.HApplication
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.shell.homework.HomeworkRepository
import com.stupidtree.hitax.shell.homework.HomeworkScreen
import com.stupidtree.hitax.agent.AgentChatViewModel
import com.stupidtree.hitax.shell.agent.AgentChatScreen
import com.stupidtree.hitax.shell.homework.HomeworkViewModel
import com.stupidtree.hitax.shell.news.ShellNewsPage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 独立 Preset Shell 入口：不经过 MainActivity 的固定四页壳。
 * 当前里程碑：五 tab 导航 + 真实今日课程页（数据来自 TimetableService）。
 */
class PresetShellActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SHELL_ID = "shellId"
    }

    private var redirectedBack = false

    override fun onResume() {
        super.onResume()
        if (redirectedBack || isFinishing) return
        val shell = com.stupidtree.hitax.market.PresetStateStore.activeShell(this)
        val current = intent.getStringExtra(EXTRA_SHELL_ID)
        if (shell.isNullOrBlank() || (current != null && shell != current)) {
            redirectedBack = true
            startActivity(
                android.content.Intent(this, com.stupidtree.hitax.ui.main.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = (application as HApplication).pluginHost.service(TimetableService.KEY)
        val homeworkViewModel = HomeworkViewModel(HomeworkRepository(application))
        val agentViewModel = AgentChatViewModel(application)
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = Color(0xFF3390EC),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEBF4FC),
                    onPrimaryContainer = Color(0xFF003366),
                    background = Color(0xFFF6F7FA),
                    surface = Color.White,
                    onSurface = Color.Black,
                    onSurfaceVariant = Color(0xFF808080),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F7FA)) {
                    HitaAndroidShell(service, homeworkViewModel, agentViewModel)
                }
            }
        }
    }
}

private data class ShellTab(val title: String, val iconRes: Int)

@Composable
private fun HitaAndroidShell(
    service: TimetableService?,
    homeworkViewModel: HomeworkViewModel,
    agentViewModel: AgentChatViewModel,
) {
    val tabs = remember { listOf(
        ShellTab("待办", R.drawable.ic_baseline_check_24),
        ShellTab("今日", R.drawable.ic_nav_today),
        ShellTab("时间表", R.drawable.ic_nav_timetable),
        ShellTab("助手", R.drawable.ic_baseline_toys_24),
        ShellTab("资讯", R.drawable.ic_nav_blog),
        ShellTab("更多", R.drawable.ic_nav_navigation),
    ) }
    var selected by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> HomeworkScreen(homeworkViewModel)
                1 -> TodayPage(service)
                2 -> TimetablePage(service)
                3 -> AgentChatScreen(agentViewModel, remember(service) { runCatching { service?.getRecentTimetable()?.value?.id }.getOrNull() })
                4 -> ShellNewsPage()
                5 -> MorePage(service)
                else -> Unit
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.BottomCenter) {
            val availableWidth = LocalConfiguration.current.screenWidthDp.dp - 32.dp
            val tabWidth = minOf(56.dp, availableWidth / tabs.size.coerceAtLeast(1))
            val barWidth = tabWidth * tabs.size + 8.dp
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selected,
                label = "shell_tab_indicator",
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.width(barWidth).height(54.dp),
            ) {
                Box(Modifier.fillMaxSize().padding(4.dp)) {
                    Surface(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(tabWidth)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF3390EC).copy(alpha = 0.12f),
                    ) {}
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        tabs.forEachIndexed { index, tab ->
                            val active = selected == index
                            val tint = if (active) Color(0xFF0088CC) else Color(0xFF171717)
                            Column(
                                Modifier
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable { selected = index }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    tab.title,
                                    color = tint,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayPage(service: TimetableService?) {
    val dateText = remember { SimpleDateFormat("M月d日 EEE", Locale.CHINA).format(Date()) }
    val events = remember(service) {
        runCatching { service?.getTodayEventsSync().orEmpty() }.getOrDefault(emptyList())
    }
    val currentWeek = remember(service) {
        runCatching {
            service?.getRecentTimetable()?.value?.let { service.getCurrentWeekOfTimetable(it.code)?.value }
        }.getOrNull()
    }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Text(
            dateText,
            color = Color(0xFF111827),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(39.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C5C91)),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF1C5E9D), Color(0xFF0C5C91)),
                    ),
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (events.isEmpty()) "今日空闲" else "今日课程",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (events.isEmpty()) {
                        Text(
                            "享受当下！",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(110.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(110.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_timeline_head_free),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_baseline_arrow_drop_down_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp).size(44.dp),
                )
                if (events.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        events.sortedBy { it.from.time }.forEach { event ->
                            EventRow(event)
                        }
                    }
                }
            }
        }
        if (events.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_timeline),
                        contentDescription = null,
                        tint = Color(0xFF3390EC).copy(alpha = 0.35f),
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("今天暂无课程或日程安排", color = Color(0xFF808080), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: EventItem) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            event.from.toString().takeLast(8).take(5),
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(event.name, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(event.place?.takeIf { it.isNotBlank() }, event.teacher?.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TimetablePage(service: TimetableService?) {
    val timetableLive = remember(service) {
        service?.getRecentTimetable()
            ?: androidx.lifecycle.MutableLiveData<com.stupidtree.hitax.data.model.timetable.Timetable?>(null)
    }
    val timetable by timetableLive.observeAsState(null)
    val currentWeekLive = remember(service, timetable) {
        service?.getCurrentWeekOfTimetable(timetable?.code)
            ?: androidx.lifecycle.MutableLiveData(1)
    }
    val currentWeek by currentWeekLive.observeAsState(1)
    val week = (currentWeek ?: 1).coerceAtLeast(1)
    val start = remember(timetable, week) {
        (timetable?.startTime?.time ?: 0L) + (week - 1).toLong() * 7 * 24 * 3600 * 1000
    }
    val eventsLive = remember(service, start) {
        service?.getEventsDuring(start, start + 7L * 24 * 3600 * 1000)
            ?: androidx.lifecycle.MutableLiveData(emptyList())
    }
    val events by eventsLive.observeAsState(emptyList())

    if (timetable == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_timeline),
                    contentDescription = null,
                    tint = Color(0xFF3390EC).copy(alpha = 0.35f),
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("还没有课表", color = Color(0xFF808080), fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("请先在功能中心导入课表", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Text(
                "第${week}周",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
            Text(timetable?.name ?: "课表", color = Color(0xFF6B7280))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 42.dp, end = 8.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, fontWeight = FontWeight.Medium)
                    val date = ((start + index * 24L * 3600 * 1000) / 1000L)
                    Text(
                        SimpleDateFormat("d", Locale.CHINA).format(Date(date * 1000L)),
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Row(Modifier.fillMaxSize().padding(top = 4.dp)) {
            Column(Modifier.width(38.dp)) {
                repeat(12) { index ->
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.TopCenter) {
                        Text("${index + 1}", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                    }
                }
            }
            repeat(7) { dayIndex ->
                Box(
                    Modifier.weight(1f).height((12 * 48).dp).padding(horizontal = 1.dp),
                ) {
                    repeat(12) { period ->
                        Box(Modifier.fillMaxWidth().height(48.dp).offset(y = (period * 48).dp)) {
                            Box(
                                Modifier.fillMaxWidth().height(1.dp)
                                    .background(Color(0x11000000)),
                            )
                        }
                    }
                    events.filter { dayOfWeek(it.from.time) == dayIndex + 1 }.forEach { item ->
                        val top = ((item.fromNumber - 1).coerceAtLeast(0)) * 48
                        val span = (item.lastNumber - item.fromNumber + 1).coerceAtLeast(1)
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(item.color).copy(alpha = 0.9f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(1.dp)
                                .offset(y = top.dp)
                                .height((48 * span - 2).dp),
                        ) {
                            Column(Modifier.padding(4.dp)) {
                                Text(
                                    item.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.place?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = Color.White.copy(alpha = .8f), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayOfWeek(time: Long): Int {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = time
    return when (c.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.SUNDAY -> 7
        else -> c.get(java.util.Calendar.DAY_OF_WEEK) - 1
    }
}

@Composable
private fun MorePage(service: TimetableService?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("local_eas_token", android.content.Context.MODE_PRIVATE) }
    val userName = remember {
        prefs.getString("name", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("username", null)?.takeIf { it.isNotBlank() }
    }
    val timetableCountLive = remember(service) {
        service?.getTimetableCount() ?: androidx.lifecycle.MutableLiveData(0)
    }
    val timetableCount by timetableCountLive.observeAsState(0)
    fun open(className: String?) {
        if (className.isNullOrBlank()) return
        runCatching {
            context.startActivity(
                android.content.Intent().setClassName(context, className)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    fun openPlugin(id: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(context, com.stupidtree.hitax.uiplugin.PluginUiActivity::class.java)
                    .putExtra(com.stupidtree.hitax.uiplugin.PluginUiActivity.EXTRA_PLUGIN_ID, id)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text("更多", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().clickable {
                open("com.stupidtree.hitax.ui.eas.login.ShenzhenWebLoginActivity")
            },
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(44.dp).height(44.dp).background(Color(0xFFE8EEFF), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("H", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(userName ?: "未登录教务", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(
                        if (userName == null) "点击进行教务登录" else "已连接教务",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("课表管理", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("共${timetableCount ?: 0}个课表", color = Color(0xFF6B7280), fontSize = 12.sp)
                }
                Text("查看", color = Color(0xFF2563EB), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        MoreGroup("教务功能") {
            MoreRow("考试", icon = Icons.Default.CalendarToday) { open("com.stupidtree.hitax.ui.eas.exam.ExamActivity") }
            MoreRow("成绩管理", "个人成绩明细 · 分项得分 · 权重折算", Icons.Default.Assessment) {
                open("com.stupidtree.hitax.ui.eas.score.ScoreInquiryActivity")
            }
            MoreRow("空教室", icon = Icons.Default.MeetingRoom) { open("com.stupidtree.hitax.ui.eas.classroom.EmptyClassroomActivity") }
            MoreRow("培养方案", "完成度 · 学分类别 · 培养课组", Icons.Default.School) {
                openPlugin("com.stupidtree.hita.ui.training")
            }
            MoreRow("深圳课程浏览", "教务选课池 · 全校课表（需 Web 登录）", Icons.Default.Search) {
                openPlugin("com.stupidtree.hita.ui.community")
            }
        }
        Spacer(Modifier.height(14.dp))
        MoreGroup("课程资源") {
            MoreRow("查询资源", "查询三个校区的课程资料", Icons.Default.Search) {
                open("com.stupidtree.hitax.ui.search.SearchActivity")
            }
        }
        Spacer(Modifier.height(14.dp))
        MoreGroup("设置") {
            MoreRow("连接深圳 Web 教务", "培养方案、考试等需要 Web 会话", Icons.Default.Link) {
                open("com.stupidtree.hitax.ui.eas.login.ShenzhenWebLoginActivity")
            }
            MoreRow("插件市场", "切换整合包、安装或停用插件", Icons.Default.Settings) {
                context.startActivity(
                    android.content.Intent(
                        context,
                        com.stupidtree.hitax.market.PluginMarketActivity::class.java,
                    )
                )
            }
            MoreRow("应用设置", "外观、提醒与数据", Icons.Default.Settings) {
                context.startActivity(
                    android.content.Intent(
                        context,
                        com.stupidtree.hitax.ui.main.MainActivity::class.java,
                    ).putExtra(
                        com.stupidtree.hitax.ui.main.MainActivity.EXTRA_SKIP_SHELL_REDIRECT,
                        true,
                    )
                )
            }
        }
    }
}

@Composable
private fun MoreGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ChevronRight,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(Color(0xFFE8F0FE), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color(0xFF3390EC), modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color(0xFF808080), fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MoreCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp)
        }
    }
}
