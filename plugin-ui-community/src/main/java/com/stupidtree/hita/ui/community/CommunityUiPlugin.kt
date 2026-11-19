package com.stupidtree.hita.ui.community

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.PluginUiHost

/**
 * 深圳课程浏览首版入口页。
 *
 * 深圳教务 Web 对移动端 UA 返回“请使用 PC 浏览器”，WebView 直开不可用；
 * 原生课程池需要后续移植 ShenzhenCourseCatalogActivity 的 EAS DTO/repository。
 * 当前提供 Web 登录与系统浏览器入口，明确标注能力边界。
 */
class CommunityUiPlugin : ComposeUiPlugin {
    override val id = "com.stupidtree.hita.ui.community"
    override val displayName = "深圳课程浏览"
    override val requiresApi = 7

    override fun createView(context: Context, host: PluginUiHost): View {
        return ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { host.close() }) { Text("返回") }
                            Text(
                                "深圳课程浏览",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Card(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("教务选课池 · 全校课表", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "深圳教务 Web 限制移动端访问；当前版本提供登录与 PC 浏览器入口。" +
                                        "原生课程池（选课任务、附件、历史挂科率）仍在移植中。",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent().setClassName(
                                                context,
                                                "com.stupidtree.hitax.ui.eas.login.ShenzhenWebLoginActivity",
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }) { Text("连接 Web 教务") }
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }) { Text("用浏览器打开教务") }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val PORTAL = "https://jw.hitsz.edu.cn/"
    }
}
