package com.yingjing.pfa.domain.alert

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
 */
object AlertRules {

    private const val MS_PER_DAY = 86_400_000L

    /** 存款临近到期。 */
    fun depositMaturity(
        holdings: List<Holding>,
        nowMs: Long,
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
                title = "存款即将到期",
                body = "「${h.name}」将在 $daysLeft 天后到期，记得续存 / 转投。",
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
        thresholds: AlertThresholds = AlertThresholds(),
    ): List<Alert> {
        val today = nowMs / MS_PER_DAY
        val result = mutableListOf<Alert>()
        changes.forEach { change ->
            if (change.oldPrice <= 0.0) return@forEach
            val pct = (change.newPrice - change.oldPrice) / change.oldPrice * 100.0
            if (abs(pct) < thresholds.holdingMovePercent) return@forEach
            val h = change.holding
            val direction = if (pct >= 0) "上涨" else "下跌"
            result += Alert(
                userId = h.userId,
                category = AlertCategory.HOLDING,
                severity = AlertSeverity.SERIOUS,
                title = "${h.name} 大幅波动",
                body = "你持有的「${h.name}」较上次${direction} ${"%.1f".format(abs(pct))}%（触发 ±${thresholds.holdingMovePercent}% 阈值）。",
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
        thresholds: AlertThresholds = AlertThresholds(),
    ): List<Alert> {
        val today = nowMs / MS_PER_DAY
        return changes.mapNotNull { (code, pct) ->
            if (abs(pct) < thresholds.marketMovePercent) return@mapNotNull null
            val name = names[code] ?: code
            val direction = if (pct >= 0) "上涨" else "下跌"
            val pctText = "%.2f".format(abs(pct))
            Alert(
                userId = userId,
                category = AlertCategory.MARKET,
                severity = AlertSeverity.SERIOUS,
                title = "$name 单日$direction $pctText%",
                body = "主要指数「$name」当日$direction $pctText%，触发 ±${thresholds.marketMovePercent}% 阈值。",
                dedupKey = "market_${code}_$today",
                createdAtEpochMs = nowMs,
            )
        }
    }
}
