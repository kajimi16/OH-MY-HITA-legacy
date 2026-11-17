package com.stupidtree.hitax.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetLayoutSyncTest {

    private fun tab(id: String, title: String, order: Int) =
        PresetTab(id = id, title = title, order = order)

    private fun preset(id: String, tabs: List<PresetTab>?) = MarketPreset(
        id = id,
        name = id,
        tabs = tabs,
    )

    private val sixTabs = listOf(
        tab("com.stupidtree.hita.ui.homework", "待办", 0),
        tab("hita.timeline", "今日", 1),
        tab("hita.timetable", "时间表", 2),
        tab("com.stupidtree.hita.ui.ai", "助手", 3),
        tab("com.stupidtree.hita.ui.news", "资讯", 4),
        tab("hita.more", "更多", 5),
    )

    @Test
    fun backfillsWhenPresetActiveButNoStoredLayout() {
        val result = PresetLayoutSync.tabsToBackfill(
            "hita-android-3.1.2",
            emptyList(),
            listOf(preset("hita-android-3.1.2", sixTabs)),
        )
        assertEquals(sixTabs, result)
    }

    @Test
    fun doesNothingWhenNoPresetActive() {
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                null,
                emptyList(),
                listOf(preset("hita-android-3.1.2", sixTabs)),
            ),
        )
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                "  ",
                emptyList(),
                listOf(preset("hita-android-3.1.2", sixTabs)),
            ),
        )
    }

    @Test
    fun doesNothingWhenStoredLayoutExists() {
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                "hita-android-3.1.2",
                listOf(tab("hita.more", "更多", 0)),
                listOf(preset("hita-android-3.1.2", sixTabs)),
            ),
        )
    }

    @Test
    fun doesNothingWhenActivePresetMissingFromIndex() {
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                "hita-android-3.1.2",
                emptyList(),
                listOf(preset("hita-l-1.5.3", sixTabs)),
            ),
        )
    }

    @Test
    fun doesNothingWhenPresetHasNoTabs() {
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                "hita-l-1.5.3",
                emptyList(),
                listOf(preset("hita-l-1.5.3", null)),
            ),
        )
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                "hita-l-1.5.3",
                emptyList(),
                listOf(preset("hita-l-1.5.3", emptyList())),
            ),
        )
    }

    @Test
    fun dropsTabsWithoutIdAndSkipsWhenAllBlank() {
        val withBlank = listOf(
            tab("", "坏数据", 0),
            tab("hita.timeline", "今日", 1),
        )
        assertEquals(
            listOf(tab("hita.timeline", "今日", 1)),
            PresetLayoutSync.tabsToBackfill(
                "hita-android-3.1.2",
                emptyList(),
                listOf(preset("hita-android-3.1.2", withBlank)),
            ),
        )
        assertNull(
            PresetLayoutSync.tabsToBackfill(
                "hita-android-3.1.2",
                emptyList(),
                listOf(preset("hita-android-3.1.2", listOf(tab("", "坏数据", 0)))),
            ),
        )
    }
}