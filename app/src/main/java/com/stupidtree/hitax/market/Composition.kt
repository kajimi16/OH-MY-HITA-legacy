package com.stupidtree.hitax.market

/** 组合条目的种类；id 只在同一种类内唯一。 */
enum class CompositionKind { BUILTIN, DEX, UI }

/** 最终值由哪一层写入；后者覆盖前者。 */
enum class CompositionSource { BASE, PRESET, USER, SAFETY }

data class CompositionEntry(
    val id: String,
    val kind: CompositionKind,
    val enabled: Boolean,
    val required: Boolean,
    val source: CompositionSource,
)

sealed class CompositionMissingReason {
    data object NotInBase : CompositionMissingReason()
    data object NotInstalled : CompositionMissingReason()
    data object NotInIndex : CompositionMissingReason()
}

data class CompositionMissing(
    val id: String,
    val kind: CompositionKind,
    val reason: CompositionMissingReason,
)

/**
 * 一次组合计算的目标状态。
 *
 * 它是纯数据：不包含下载、安装或启用动作；materializer / reconciler 再决定怎么做。
 */
data class CompositionPlan(
    val entries: List<CompositionEntry>,
    val missing: List<CompositionMissing>,
) {
    /**
     * 会阻止应用 preset 的必选缺失。
     *
     * `NotInstalled` 是可安装缺口，必须交给 materializer；只有“索引/清单里根本没有”
     * 才是预检阶段无法解决的阻塞。
     */
    fun blockingRequiredMissing(): List<CompositionMissing> = missing.filter { item ->
        item.reason != CompositionMissingReason.NotInstalled &&
            entries.any { it.id == item.id && it.kind == item.kind && it.required }
    }

    fun dump(): String = buildString {
        appendLine("entries:")
        entries.forEach {
            appendLine(
                "  ${it.kind.name.lowercase()} ${it.id} enabled=${it.enabled} " +
                    "source=${it.source.name.lowercase()} required=${it.required}",
            )
        }
        appendLine("missing:")
        missing.forEach {
            appendLine("  ${it.kind.name.lowercase()} ${it.id} reason=${it.reason::class.simpleName}")
        }
    }.trimEnd()
}

/**
 * 只做“组合应该是什么”的计算，不读写磁盘、不访问 Android。
 *
 * 输入是当前事实（base / 已安装 / 索引可用 / 用户禁用 / kill switch）与前后两个 preset。
 * 输出覆盖 base、preset 引用以及“旧 preset 独有条目”，未提到的已安装 UI 插件保持原状。
 */
object CompositionResolver {

    fun resolve(
        baseIds: Set<String>,
        next: MarketPreset?,
        previous: MarketPreset?,
        available: Map<CompositionKind, Set<String>>,
        installed: Map<CompositionKind, Set<String>>,
        userDisabled: Map<CompositionKind, Set<String>> = emptyMap(),
        killSwitches: Set<String> = emptySet(),
    ): CompositionPlan {
        val entries = LinkedHashMap<Pair<CompositionKind, String>, CompositionEntry>()

        baseIds.forEach { id ->
            entries[CompositionKind.BUILTIN to id] = CompositionEntry(
                id = id,
                kind = CompositionKind.BUILTIN,
                enabled = true,
                required = false,
                source = CompositionSource.BASE,
            )
        }

        val nextRefs = next?.refs().orEmpty().associateBy { it.compositionKey() }
        previous?.refs().orEmpty()
            .filterNot { it.compositionKey() in nextRefs }
            .forEach { ref ->
                val key = ref.compositionKey()
                entries[key] = CompositionEntry(
                    id = ref.id,
                    kind = key.first,
                    enabled = false,
                    required = false,
                    source = CompositionSource.PRESET,
                )
            }

        next?.refs().orEmpty().forEach { ref ->
            val key = ref.compositionKey()
            entries[key] = CompositionEntry(
                id = ref.id,
                kind = key.first,
                enabled = true,
                required = ref.isRequired(),
                source = CompositionSource.PRESET,
            )
        }

        val missing = mutableListOf<CompositionMissing>()
        next?.refs().orEmpty().forEach { ref ->
            val kind = ref.kind()
            val installedNow = kind != CompositionKind.BUILTIN && ref.id in installed[kind].orEmpty()
            val known = when (kind) {
                CompositionKind.BUILTIN -> ref.id in baseIds
                // 已安装但暂时不在索引里的插件仍可启用，不能误报为缺失。
                CompositionKind.DEX, CompositionKind.UI -> installedNow || ref.id in available[kind].orEmpty()
            }
            if (!known) {
                missing += CompositionMissing(
                    ref.id,
                    kind,
                    if (kind == CompositionKind.BUILTIN) {
                        CompositionMissingReason.NotInBase
                    } else {
                        CompositionMissingReason.NotInIndex
                    },
                )
            } else if (kind != CompositionKind.BUILTIN && !installedNow) {
                missing += CompositionMissing(ref.id, kind, CompositionMissingReason.NotInstalled)
            }
        }

        val resolved = entries.values.map { entry ->
            when {
                entry.id in killSwitches -> entry.copy(
                    enabled = false,
                    source = CompositionSource.SAFETY,
                )
                entry.id in userDisabled[entry.kind].orEmpty() -> entry.copy(
                    enabled = false,
                    source = CompositionSource.USER,
                )
                else -> entry
            }
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.id }))

        return CompositionPlan(
            entries = resolved,
            missing = missing.sortedWith(compareBy({ it.kind.ordinal }, { it.id })),
        )
    }

    private fun PresetPluginRef.compositionKey(): Pair<CompositionKind, String> = kind() to id

    private fun PresetPluginRef.kind(): CompositionKind = when (normalizedKind()) {
        "dex" -> CompositionKind.DEX
        "builtin" -> CompositionKind.BUILTIN
        else -> CompositionKind.UI
    }
}
