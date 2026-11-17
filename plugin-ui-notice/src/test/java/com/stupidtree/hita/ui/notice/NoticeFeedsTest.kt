package com.stupidtree.hita.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeFeedsTest {

    @Test
    fun shenzhenIsSupportedWithListUrl() {
        val feed = NoticeFeeds.of("SHENZHEN")
        assertTrue(feed.supported)
        assertNotNull(feed.listUrl)
        assertEquals(NoticeParser.LIST, feed.listUrl?.invoke(1))
        assertNull(NoticeFeeds.unsupportedMessage(feed))
    }

    @Test
    fun benbuAndWeihaiAreUnsupportedWithMessage() {
        listOf("BENBU" to "本部", "WEIHAI" to "威海").forEach { (campus, label) ->
            val feed = NoticeFeeds.of(campus)
            assertFalse(feed.supported)
            assertNull(feed.listUrl)
            assertEquals("该校区通知尚未移植（$label）", NoticeFeeds.unsupportedMessage(feed))
        }
    }

    @Test
    fun unknownCampusFallsBackToShenzhen() {
        assertTrue(NoticeFeeds.of(null).supported)
        assertTrue(NoticeFeeds.of("  whatever ").supported)
        assertTrue(NoticeFeeds.of("shenzhen").supported)
    }
}
