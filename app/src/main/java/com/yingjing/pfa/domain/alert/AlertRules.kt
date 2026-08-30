package com.yingjing.pfa.domain.alert

import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.domain.model.AlertCategory
import com.yingjing.pfa.domain.model.AlertSeverity
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import kotlin.math.abs

/** 某持仓一次价格变化（用于判断大幅波动）。 */
data class PriceChange(val holding: Holding, val oldPrice: Double, val newPrice: Double)

/** 提醒阈值（可配置；先用默认值）。 */
data class AlertThresholds(
    val depositMaturityDays: List<Int> = listOf(30, 7),
    val holdingMovePercent: Double = 7.0,
    val marketMovePercent: Double = 3.0,
)

/**
 * 持仓相关提醒规则（纯函数，可单元测试）。
 * dedupKey 含自然日，保证「同一天同一事项」只提醒一次。
 * 所有面向用户的文案经 [resolver] 按当前 locale 解析——提醒一旦生成即落库为最终文案
 * （历史提醒保持生成时的语言，不改 locale 不重渲染）。
 */
object AlertRules {

    private const val MS_PER_DAY = 86_400_000L

    /** 存款临近到期。 */
    fun depositMaturity(
        holdings: List<Holding>,
        nowMs: Long,
        resolver: StringResolver,
        thresholds: AlertThresholds = AlertThresholds(),
    ): List<Alert> {
        val today = nowMs / MS_PER_DAY
        val result = mutableListOf<Alert>()
        holdings.filter { it.type == AssetType.DEPOSIT && it.maturityDateEpochMs != null }.forEach { h ->
            val daysLeft = (h.maturityDateEpochMs!! / MS_PER_DAY - today)
            val hitBucket = thresholds.depositMaturityDays.filter { daysLeft in 0..it }.minOrNull()
                ?: return@forEach
            result += Alert(
                userId = h.userId,
                category = AlertCategory.HOLDING,
                severity = if (daysLeft <= 7) AlertSeverity.WARNING else AlertSeverity.INFO,
                title = resolver.get(R.string.alert_deposit_title),
                body = resolver.get(R.string.alert_deposit_body, h.name, daysLeft),
                refHoldingId = h.id,
                dedupKey = "deposit_maturity_${h.id}_$hitBucket",
                createdAtEpochMs = nowMs,
            )
        }
        return result
    }

    /** 持仓当日大幅波动。 */
    fun priceMoves(
        changes: List<PriceChange>,
        nowMs: Long,
        resolver: StringResolver,
        thresholds: AlertThresholds = AlertThresholds(),
    ): List<Alert> {
        val today = nowMs / MS_PER_DAY
        val result = mutableListOf<Alert>()
        changes.forEach { change ->
            if (change.oldPrice <= 0.0) return@forEach
            val pct = (change.newPrice - change.oldPrice) / change.oldPrice * 100.0
            if (abs(pct) < thresholds.holdingMovePercent) return@forEach
            val h = change.holding
            val directionRes = if (pct >= 0) R.string.alert_price_up else R.string.alert_price_down
            val direction = resolver.get(directionRes)
            result += Alert(
                userId = h.userId,
                category = AlertCategory.HOLDING,
                severity = AlertSeverity.SERIOUS,
                title = resolver.get(R.string.alert_price_title, h.name),
                body = resolver.get(
                    R.string.alert_price_body,
                    h.name,
                    direction,
                    abs(pct),
                    thresholds.holdingMovePercent,
                ),
                refHoldingId = h.id,
                dedupKey = "price_move_${h.id}_$today",
                createdAtEpochMs = nowMs,
            )
        }
        return result
    }

    /** 主要指数单日大幅波动。changes: 指数代码 → 当日涨跌%；names: 指数代码 → 展示名。 */
    fun marketMoves(
        userId: Long,
        changes: Map<String, Double>,
        names: Map<String, String>,
        nowMs: Long,
        resolver: StringResolver,
        thresholds: AlertThresholds = AlertThresholds(),
    ): List<Alert> {
        val today = nowMs / MS_PER_DAY
        return changes.mapNotNull { (code, pct) ->
            if (abs(pct) < thresholds.marketMovePercent) return@mapNotNull null
            val name = names[code] ?: code
            val directionRes = if (pct >= 0) R.string.alert_price_up else R.string.alert_price_down
            val direction = resolver.get(directionRes)
            val pctText = "%.2f".format(abs(pct))
            Alert(
                userId = userId,
                category = AlertCategory.MARKET,
                severity = AlertSeverity.SERIOUS,
                title = resolver.get(R.string.alert_market_title, name, direction, pctText),
                body = resolver.get(R.string.alert_market_body, name, direction, pctText, thresholds.marketMovePercent),
                dedupKey = "market_${code}_$today",
                createdAtEpochMs = nowMs,
            )
        }
    }

    /** 今日可申购新股。todayDate 为 yyyy-MM-dd。 */
    fun ipoAlerts(
        userId: Long,
        ipos: List<IpoItem>,
        todayDate: String,
        nowMs: Long,
        resolver: StringResolver,
    ): List<Alert> {
        val todays = ipos.filter { it.applyDate == todayDate }
        if (todays.isEmpty()) return emptyList()
        val sep = resolver.get(R.string.alert_ipo_sep)
        val list = todays.joinToString(sep) {
            resolver.get(R.string.alert_ipo_item, it.name, it.applyCode)
        }
        return listOf(
            Alert(
                userId = userId,
                category = AlertCategory.IPO,
                severity = AlertSeverity.INFO,
                title = resolver.get(R.string.alert_ipo_title, todays.size),
                body = resolver.get(R.string.alert_ipo_body, list),
                dedupKey = "ipo_$todayDate",
                createdAtEpochMs = nowMs,
            ),
        )
    }
}
