package com.yingjing.pfa.domain.model

/** 提醒分类。 */
enum class AlertCategory(val displayName: String) {
    HOLDING("持仓"),
    IPO("新股"),
    MARKET("市场"),
    GLOBAL("国际"),
}

/** 提醒严重度（对应 UI 颜色 / 图标）。 */
enum class AlertSeverity {
    INFO,
    WARNING,
    SERIOUS,
}

/** 一条提醒。 */
data class Alert(
    val id: Long = 0,
    val userId: Long,
    val category: AlertCategory,
    val severity: AlertSeverity,
    val title: String,
    val body: String,
    val refHoldingId: Long? = null,
    /** 去重键（同一用户同一 key 只保留一条）。 */
    val dedupKey: String,
    val createdAtEpochMs: Long,
    val read: Boolean = false,
)
