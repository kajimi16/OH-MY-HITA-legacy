package com.stupidtree.hita.kernel

import com.stupidtree.hita.api.Plugin

/**
 * 插件在激活前可读的依赖声明。
 *
 * [requires] / [provides] 使用 `ServiceKey.name`，因为元数据要跨 dex/JSON 边界，
 * 不能引用 Kotlin 类型。没有声明的旧插件按空集合处理，行为与引入本类型前一致。
 */
data class PluginDescriptor(
    val id: String,
    val requires: Set<String> = emptySet(),
    val provides: Set<String> = emptySet(),
)

/** 一次带声明的安装请求。 */
data class PluginInstallation(
    val plugin: Plugin,
    val descriptor: PluginDescriptor,
)
