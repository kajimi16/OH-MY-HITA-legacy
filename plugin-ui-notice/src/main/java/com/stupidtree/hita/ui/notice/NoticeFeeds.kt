package com.stupidtree.hita.ui.notice

import android.content.Context

/**
 * 通知源按校区选择。
 *
 * 深圳沿用现有 `info.hitsz.edu.cn` 门户解析；本部/威海的通知解析器（上游
 * `BenbuNoticeParser` / `WeihaiNoticeParser`）尚未移植，这里显式标记不支持并给出提示，
 * 避免把别的校区当成深圳去抓。
 */
object NoticeFeeds {

    data class Feed(
        val campus: String,
        val label: String,
        /** 列表页地址构造函数；不支持时为 null。 */
        val listUrl: ((Int) -> String)?,
        val supported: Boolean,
    )

    private val SHENZHEN = Feed(
        campus = "SHENZHEN",
        label = "深圳",
        listUrl = { page ->
            if (page <= 1) NoticeParser.LIST
            else NoticeParser.listUrl(page)
        },
        supported = true,
    )

    private val BENBU = Feed("BENBU", "本部", null, false)
    private val WEIHAI = Feed("WEIHAI", "威海", null, false)

    fun of(campus: String?): Feed = when (campus?.trim()?.uppercase()) {
        "BENBU" -> BENBU
        "WEIHAI" -> WEIHAI
        else -> SHENZHEN
    }

    /** 从教务凭据偏好读取当前校区；缺省按深圳处理，与 Campus.from 保持一致。 */
    fun currentCampus(context: Context): String =
        context.applicationContext
            .getSharedPreferences("local_eas_token", Context.MODE_PRIVATE)
            .getString("campus", null)
            ?.trim()
            ?.uppercase()
            ?: "SHENZHEN"

    fun unsupportedMessage(feed: Feed): String? =
        if (feed.supported) null else "该校区通知尚未移植（${feed.label}）"
}
