package com.yingjing.pfa.data.sync

import com.yingjing.pfa.data.remote.MarketIndexes
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.alert.AlertRules
import com.yingjing.pfa.domain.alert.PriceChange
import com.yingjing.pfa.data.remote.IpoRemote
import com.yingjing.pfa.data.remote.MarketIndexRemote
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.HousePriceCities
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.LiabilityRepayment
import com.yingjing.pfa.domain.usecase.RealEstateEstimator
import com.yingjing.pfa.domain.usecase.SummarizePortfolio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日同步：刷新汇率 + 抓取现价写回 + 记录净值快照 + 生成持仓提醒（到期 / 大幅波动）并通知。
 *
 * 房产估算：sync 前批量刷新 70 城房价指数；per-user 循环内对开启估算的房产按二手环比
 * 累乘得到估算现值并写回 estimatedValue，随后快照自动反映。
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
    private val housePriceRepository: HousePriceRepository,
) {
    suspend fun sync(): Boolean = runCatching {
        fxRepository.refresh()
        val rates = fxRepository.current()
        val indexChanges = runCatching { marketIndexRemote.fetch() }.getOrDefault(emptyMap())
        val ipos = runCatching { ipoRemote.fetch() }.getOrDefault(emptyList())
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(java.util.Date(nowProvider()))

        // 预读所有用户持仓，收集需要刷新指数的城市（去重 + 70 城校验）后批量刷新
        val users = userRepository.listUsers()
        val allHoldingsByUser = users.associate { it.id to holdingRepository.observeHoldingsSnapshot(it.id) }
        val cities = allHoldingsByUser.values.flatten()
            .filter { it.type == AssetType.REAL_ESTATE && it.autoEstimate == true && HousePriceCities.contains(it.city) }
            .mapNotNull { it.city }
            .distinct()
        if (cities.isNotEmpty()) {
            runCatching { housePriceRepository.refresh(cities) }
        }

        users.forEach { user ->
            val holdings = allHoldingsByUser[user.id].orEmpty()
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
            // 房产指数估算写回：取该城指数历史 → 累乘二手环比 → 写 estimatedValue
            val estimated = repaid.map { holding -> estimateAndWrite(holding, now) ?: holding }
            val summary = SummarizePortfolio(estimated, rates, user.defaultCurrency, now)
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

    /**
     * 对开启估算的房产计算估算值并写回。返回更新后的 holding；不满足条件返回 null（保持原值）。
     */
    private suspend fun estimateAndWrite(holding: com.yingjing.pfa.domain.model.Holding, now: Long): com.yingjing.pfa.domain.model.Holding? {
        if (holding.type != AssetType.REAL_ESTATE) return null
        if (holding.autoEstimate != true) return null
        val city = holding.city ?: return null
        if (!HousePriceCities.contains(city)) return null
        val manualValue = holding.manualValue ?: return null
        val baseDateMs = holding.valueBaseDateEpochMs ?: return null

        val history = runCatching { housePriceRepository.history(city) }.getOrDefault(emptyList())
        val estimatedValue = RealEstateEstimator.estimate(manualValue, baseDateMs, history, now)
            ?: return null
        if (estimatedValue == holding.estimatedValue) return null
        val next = holding.copy(estimatedValue = estimatedValue)
        holdingRepository.updateHolding(next)
        return next
    }

    /** 便于测试覆盖的时间源。 */
    var nowProvider: () -> Long = { System.currentTimeMillis() }
}

