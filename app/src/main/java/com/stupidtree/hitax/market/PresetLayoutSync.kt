package com.stupidtree.hitax.market

/**
 * 预设布局的存量补写判定（纯逻辑，便于单测）。
 *
 * 背景：`tabs` 只在“应用整合包成功”时写入本地（`PluginMarketViewModel` → `PresetLayoutStore.save`）。
 * 老版本宿主、或索引尚未发布 `tabs` 时装过整合包的用户，本地只有 `preset_id`、没有 `tabs_json`。
 * 索引补上 `tabs` 之后如果不做补写，这些用户的底栏会一直停在内置的“4 个基页 + 插件 tab”，
 * 必须手动重新应用整合包才生效——这与“只启用整合包就应与 3.1.2 一致”的目标矛盾。
 */
object PresetLayoutSync {

    /**
     * 判断是否需要把索引里的 `tabs` 补写到本地。
     *
     * @param activeId 当前生效的整合包 id，可为空（没有启用任何预设）
     * @param stored 本地已保存的布局
     * @param presets 索引中的预设列表
     * @return 需要补写的 tab 列表；不需要补写时返回 null
     */
    fun tabsToBackfill(
        activeId: String?,
        stored: List<PresetTab>,
        presets: List<MarketPreset>,
    ): List<PresetTab>? {
        if (activeId.isNullOrBlank()) return null
        // 本地已有布局：以本地为准，避免覆盖用户手动调整过的结果
        if (stored.isNotEmpty()) return null
        val preset = presets.firstOrNull { it.id == activeId } ?: return null
        val tabs = preset.tabs.orEmpty().filter { it.id.isNotBlank() }
        return tabs.takeIf { it.isNotEmpty() }
    }
}