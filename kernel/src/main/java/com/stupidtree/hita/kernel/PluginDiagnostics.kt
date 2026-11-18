package com.stupidtree.hita.kernel

/** 插件失败发生在哪个阶段。 */
enum class PluginFailurePhase {
    /** 元数据/API/ABI 不兼容，尚未加载类。 */
    COMPATIBILITY,

    /** 类加载、入口类实例化失败。 */
    LOAD,

    /** 入口类已实例化，但 `install()` 抛异常或内核拒绝安装。 */
    INSTALL,
}

data class PluginSnapshot(
    val id: String,
    val requiresApi: Int,
)

data class PluginFailure(
    val id: String,
    val phase: PluginFailurePhase,
    val message: String,
)

data class PluginServiceBinding(
    val service: String,
    /** 插件 id，或 [PluginHost.HOST_PROVIDER]。 */
    val provider: String,
)

data class PluginWaiting(
    val id: String,
    val missing: List<String>,
)

data class PluginConflict(
    val service: String,
    val existingProvider: String,
    val rejectedProvider: String,
)

data class PluginDegraded(
    val id: String,
    val missing: List<String>,
)

/**
 * 内核当前状态的一次不可变快照。
 *
 * `waiting` / `conflicts` 在依赖声明落地后填充；当前先保留稳定字段，
 * 让诊断输出与后续实现共用同一形状。
 */
data class PluginDiagnostics(
    val installed: List<PluginSnapshot>,
    val failed: List<PluginFailure>,
    val waiting: List<PluginWaiting>,
    val conflicts: List<PluginConflict>,
    val degraded: List<PluginDegraded>,
    val services: List<PluginServiceBinding>,
)
