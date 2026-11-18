package com.stupidtree.hitax.shell.news

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private data class BlogItem(val title: String, val url: String, val date: String)

/** HITA_Android 原版资讯页的 Shell 入口：HOA 资讯 + 校园通知两个分页。 */
@Composable
internal fun ShellNewsPage() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("资讯") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("通知") })
        }
        when (tab) {
            0 -> BlogTab()
            else -> NoticeTab()
        }
    }
}

@Composable
private fun BlogTab() {
    val context = LocalContext.current
    var articles by remember { mutableStateOf<List<BlogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { fetchBlog() } }
            .onSuccess { articles = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HOA 资讯", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { reload++ }) { Text("刷新") }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> EmptyMessage("加载失败：$error") { reload++ }
            articles.isEmpty() -> EmptyMessage("暂无文章") { reload++ }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(articles, key = { it.url }) { article ->
                    Card(
                        Modifier.fillMaxWidth().clickable { openUrl(context, article.url) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(article.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(article.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeTab() {
    val context = LocalContext.current
    var cookie by remember { mutableStateOf(readCookie()) }
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        CookieManager.getInstance().flush()
        cookie = readCookie()
    }
    var notices by remember { mutableStateOf<List<CampusNotice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(cookie, reload) {
        if (cookie.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { NoticeFetcher.fetch(cookie) } }
            .onSuccess { notices = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("校园通知", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (cookie.isNotBlank()) {
                TextButton(onClick = { reload++ }) { Text("刷新") }
            }
        }
        if (cookie.isBlank()) {
            EmptyMessage("需要先登录 info.hitsz.edu.cn", "登录门户") {
                loginLauncher.launch(Intent(context, ShellPortalLoginActivity::class.java))
            }
            return@Column
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> EmptyMessage("加载失败：$error") { reload++ }
            notices.isEmpty() -> EmptyMessage("暂无通知") { reload++ }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notices, key = { it.id }) { notice ->
                    Card(
                        Modifier.fillMaxWidth().clickable { openUrl(context, notice.url) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(notice.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            if (notice.date.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(notice.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String, action: String = "重试", onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun readCookie(): String = runCatching {
    CookieManager.getInstance().getCookie(NoticeParser.PORTAL).orEmpty()
}.getOrDefault("")

private fun fetchBlog(): List<BlogItem> {
    val conn = URL("https://hoa.moe/blog/rss.xml").openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 20_000
    conn.setRequestProperty("User-Agent", "OH-MY-HITA-News/0.7")
    return try {
        val xml = conn.inputStream.bufferedReader().use { it.readText() }
        Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .mapNotNull { item ->
                val body = item.groupValues[1]
                val title = tag(body, "title")?.let { Html.fromHtml(it).toString() } ?: return@mapNotNull null
                val url = tag(body, "link") ?: return@mapNotNull null
                BlogItem(title, url, formatDate(tag(body, "pubDate")))
            }
            .take(30)
            .toList()
    } finally {
        conn.disconnect()
    }
}

private fun tag(body: String, name: String): String? =
    Regex("<$name>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$name>", RegexOption.DOT_MATCHES_ALL)
        .find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

private fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
    parser.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    val date = runCatching { parser.parse(raw) }.getOrNull() ?: return raw
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(date)
}
