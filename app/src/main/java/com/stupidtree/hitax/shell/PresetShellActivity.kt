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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stupidtree.hita.api.TimetableService
import com.stupidtree.hitax.HApplication
import com.stupidtree.hitax.data.model.timetable.EventItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 独立 Preset Shell 入口：不经过 MainActivity 的固定四页壳。
 * 当前里程碑：五 tab 导航 + 真实今日课程页（数据来自 TimetableService）。
 */
class PresetShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = (application as HApplication).pluginHost.service(TimetableService.KEY)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F7FB)) {
                    HitaAndroidShell(service)
                }
            }
        }
    }
}

private data class ShellTab(val title: String, val glyph: String)

@Composable
private fun HitaAndroidShell(service: TimetableService?) {
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
                1 -> TodayPage(service)
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
    val dateText = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()) }
    val events = remember(service) {
        runCatching { service?.getTodayEventsSync().orEmpty() }.getOrDefault(emptyList())
    }
    val currentWeek = remember(service) {
        runCatching {
            service?.getRecentTimetable()?.value?.let { service.getCurrentWeekOfTimetable(it.code)?.value }
        }.getOrNull()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(dateText, color = Color(0xFF6B7280))
        Text("今日", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (currentWeek != null) "第 $currentWeek 教学周" else "课表未同步",
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(if (events.isEmpty()) "今日空闲" else "今日课程", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (events.isEmpty()) {
                    Text("没有课程安排", color = Color(0xFF6B7280))
                } else {
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
private fun StubPage(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title 页面正在移植", color = Color(0xFF6B7280))
    }
}
