package com.yingjing.pfa.data.sync

import com.yingjing.pfa.data.remote.MarketIndexes
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.alert.AlertRules
import com.yingjing.pfa.domain.alert.PriceChange
import com.yingjing.pfa.data.remote.IpoRemote
import com.yingjing.pfa.data.remote.MarketIndexRemote
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.LiabilityRepayment
import com.yingjing.pfa.domain.usecase.SummarizePortfolio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日同步：刷新汇率 + 抓取现价写回 + 记录净值快照 + 生成持仓提醒（到期 / 大幅波动）并通知。
 *
 * 省流量：只抓实际持仓的标的，批量合并；失败静默降级（保留旧值）。
 */
@Singleton
class SyncManager @Inject constructor(
    private val userRepository: UserRepository,
    private val holdingRepository: HoldingRepository,
    private val quoteRepository: QuoteRepository,
    private val fxRepository: FxRepository,
    private val marketIndexRemote: MarketIndexRemote,
    private val ipoRemote: IpoRemote,
    private val snapshotRepository: SnapshotRepository,
    private val alertRepository: AlertRepository,
    private val alertNotifier: AlertNotifier,
    private val syncStateStore: SyncStateStore,
) {
    suspend fun sync(): Boolean = runCatching {
        fxRepository.refresh()
        val rates = fxRepository.current()
        val indexChanges = runCatching { marketIndexRemote.fetch() }.getOrDefault(emptyMap())
        val ipos = runCatching { ipoRemote.fetch() }.getOrDefault(emptyList())
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(java.util.Date(nowProvider()))

        userRepository.listUsers().forEach { user ->
            val holdings = holdingRepository.observeHoldingsSnapshot(user.id)
            val prices = quoteRepository.fetchPrices(holdings)

            val priceChanges = mutableListOf<PriceChange>()
            val updated = holdings.map { holding ->
                val newPrice = prices[holding.id]
                if (newPrice != null) {
                    holding.currentPrice?.let { old -> priceChanges += PriceChange(holding, old, newPrice) }
                    val next = holding.copy(currentPrice = newPrice)
                    holdingRepository.updateHolding(next)
                    next
                } else {
                    holding
                }
            }

            val now = nowProvider()
            // 负债自动还款：到还款日从欠款本金扣「每月还款本金」（补扣错过月份，扣到 0 为止）
            val repaid = updated.map { holding ->
                LiabilityRepayment.settle(holding, now)?.also { holdingRepository.updateHolding(it) } ?: holding
            }
            val summary = SummarizePortfolio(repaid, rates, user.defaultCurrency, now)
            snapshotRepository.record(
                userId = user.id,
                currency = user.defaultCurrency,
                totalAssets = summary.totalAssets,
                totalLiabilities = summary.totalLiabilities,
                netWorth = summary.netWorth,
                categoryAmounts = summary.byCategory.associate { it.category.name to it.amount },
                nowMs = now,
            )

            val alerts = AlertRules.depositMaturity(updated, now) +
                AlertRules.priceMoves(priceChanges, now) +
                AlertRules.marketMoves(user.id, indexChanges, MarketIndexes.NAMES, now) +
                AlertRules.ipoAlerts(user.id, ipos, todayDate, now)
            alerts.forEach { alert ->
                if (alertRepository.insertIfNew(alert)) alertNotifier.notify(alert)
            }
        }
        syncStateStore.setLastSync(nowProvider())
        true
    }.getOrDefault(false)

    /** 便于测试覆盖的时间源。 */
    var nowProvider: () -> Long = { System.currentTimeMillis() }
}
