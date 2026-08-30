package com.yingjing.pfa.domain.model

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/** 提醒分类。 */
enum class AlertCategory(@StringRes val displayRes: Int) {
    HOLDING(R.string.alert_cat_holding),
    IPO(R.string.alert_cat_ipo),
    MARKET(R.string.alert_cat_market),
    GLOBAL(R.string.alert_cat_global),
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
