package com.stupidtree.hitax.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import com.stupidtree.hita.api.TimetableService
import com.stupidtree.hitax.HApplication
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.shell.homework.HomeworkRepository
import com.stupidtree.hitax.shell.homework.HomeworkScreen
import com.stupidtree.hitax.shell.homework.HomeworkViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 独立 Preset Shell 入口：不经过 MainActivity 的固定四页壳。
 * 当前里程碑：五 tab 导航 + 真实今日课程页（数据来自 TimetableService）。
 */
class PresetShellActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SHELL_ID = "shellId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = (application as HApplication).pluginHost.service(TimetableService.KEY)
        val homeworkViewModel = HomeworkViewModel(HomeworkRepository(application))
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F7FB)) {
                    HitaAndroidShell(service, homeworkViewModel)
                }
            }
        }
    }
}

private data class ShellTab(val title: String, val glyph: String)

@Composable
private fun HitaAndroidShell(
    service: TimetableService?,
    homeworkViewModel: HomeworkViewModel,
) {
    val tabs = remember { listOf(
        ShellTab("待办", "✓"),
        ShellTab("今日", "●"),
        ShellTab("时间表", "▦"),
        ShellTab("助手", "✦"),
        ShellTab("更多", "⋯"),
    ) }
    var selected by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> HomeworkScreen(homeworkViewModel)
                1 -> TodayPage(service)
                2 -> TimetablePage(service)
                else -> StubPage(tabs[selected].title)
            }
        }
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Text(tab.glyph) },
                    label = { Text(tab.title) },
                )
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
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(38.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    if (events.isEmpty()) "今日空闲" else "今日课程",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (events.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    events.sortedBy { it.from.time }.forEach { event ->
                        EventRow(event)
                    }
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
private fun StubPage(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title 页面正在移植", color = Color(0xFF6B7280))
    }
}
