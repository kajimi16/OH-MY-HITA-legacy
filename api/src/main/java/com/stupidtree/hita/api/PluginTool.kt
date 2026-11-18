package com.stupidtree.hita.api

/**
 * 插件贡献工具时使用的纯 DTO。`:api` 不能引用 `:app` 的 ToolSpec，
 * 这里只描述 schema 与字符串处理函数，由宿主适配到内部工具注册表。
 */
data class PluginToolParam(
    val name: String,
    /** string / integer / number / boolean */
    val type: String = "string",
    val required: Boolean = false,
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val enumValues: List<String> = emptyList(),
)

data class PluginToolSpec(
    val name: String,
    val description: String,
    val parameters: List<PluginToolParam> = emptyList(),
    val maxObservationChars: Int = 1800,
    val timeoutMs: Long = 15_000,
)

fun interface PluginToolHandler {
    /** inputJson 是模型给出的动作输入，返回观察文本；抛异常会被宿主转成工具错误。 */
    fun execute(inputJson: String): String?
}

interface PluginToolRegistry {
    fun register(spec: PluginToolSpec, handler: PluginToolHandler): AutoCloseable
}
