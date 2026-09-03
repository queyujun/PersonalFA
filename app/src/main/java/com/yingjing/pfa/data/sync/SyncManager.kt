package com.yingjing.pfa.data.sync

import android.util.Log
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.data.remote.CommodityRemote
import com.yingjing.pfa.data.remote.GlobalIndicators
import com.yingjing.pfa.data.remote.IpoRemote
import com.yingjing.pfa.data.remote.MarketIndexes
import com.yingjing.pfa.data.remote.MarketIndexRemote
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.alert.AlertRules
import com.yingjing.pfa.domain.alert.PriceChange
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.HousePriceCities
import com.yingjing.pfa.domain.model.HousePriceSyncNote
import com.yingjing.pfa.domain.model.HousePriceSyncStatus
import com.yingjing.pfa.domain.model.SyncResult
import com.yingjing.pfa.domain.model.SyncSource
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.HouseRefreshOutcome
import com.yingjing.pfa.domain.repository.HouseRefreshResult
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.SubscriptionRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.LiabilityRepayment
import com.yingjing.pfa.domain.usecase.RealEstateEstimator
import com.yingjing.pfa.domain.usecase.SubscriptionRenewal
import com.yingjing.pfa.domain.usecase.SummarizePortfolio
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日同步：刷新汇率 + 抓取现价写回 + 记录净值快照 + 生成持仓提醒（到期 / 大幅波动）并通知。
 *
 * 顶层各数据源并行抓取：fx / 市场指数 / 贵金属 / IPO / 房价 互不阻塞，海外源超时只拖自己。
 * 每个源用 runCatching 兜底，失败计入 [SyncResult.failedSources] 上报 UI 提示框。
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
    private val subscriptionRepository: SubscriptionRepository,
    private val alertRepository: AlertRepository,
    private val alertNotifier: AlertNotifier,
    private val syncStateStore: SyncStateStore,
    private val housePriceRepository: HousePriceRepository,
    private val stringResolver: StringResolver,
    private val commodityRemote: CommodityRemote,
    private val globalRatesStore: GlobalRatesStore,
) {
    /**
     * 执行一次同步。[manual] 标记是否由用户手动触发（手动触发时即便全成功也弹窗确认）。
     * 返回本次同步的细分结果（成败 + 失败源 + 完成时刻），并持久化到 [syncStateStore]。
     */
    suspend fun sync(manual: Boolean = false): SyncResult = runCatching {
        val failedSources = mutableListOf<SyncSource>()
        var housePriceNote: HousePriceSyncNote? = null

        // 顶层并行抓取：各数据源互不阻塞，海外源超时只拖自己。
        coroutineScope {
            val fxJob = async {
                runCatching { fxRepository.refresh() }
                    .onFailure { Log.w(TAG, "fx refresh failed", it) }
                    .getOrNull()
            }
            val indexJob = async { fetchIndexChangesWithRetry() }
            val commodityJob = async {
                runCatching { commodityRemote.fetch() }
                    .onFailure { Log.w(TAG, "commodity fetch failed", it) }
                    .getOrDefault(emptyMap())
            }
            val ipoJob = async {
                runCatching { ipoRemote.fetch() }
                    .onFailure { Log.w(TAG, "ipo fetch failed", it) }
                    .getOrDefault(emptyList())
            }

            val rates = fxJob.await() ?: fxRepository.current()
            // FX 失败判定：刷新后仍为默认 1.0/1.0（未取到有效汇率）
            if (rates.usdToCny == DEFAULT_FX.usdToCny && rates.hkdToCny == DEFAULT_FX.hkdToCny) {
                failedSources += SyncSource.FX
            }

            val indexChanges = indexJob.await()
            if (indexChanges.isEmpty()) failedSources += SyncSource.MARKET_INDEX

            val commodityChanges = commodityJob.await()
            if (commodityChanges.isEmpty()) failedSources += SyncSource.COMMODITY

            val ipos = ipoJob.await()
            // IPO 无失败判定：每日可申购数本来就常为 0，空列表属正常，不计失败。

            val globalChanges = buildGlobalChanges(rates, commodityChanges)
            val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(java.util.Date(nowProvider()))

            // 预读所有用户持仓，收集需要刷新指数的城市（去重 + 70 城校验）后批量刷新
            val users = userRepository.listUsers()
            val allHoldingsByUser = users.associate { it.id to holdingRepository.observeHoldingsSnapshot(it.id) }
            val cities = allHoldingsByUser.values.flatten()
                .filter { it.type == AssetType.REAL_ESTATE && it.autoEstimate == true && HousePriceCities.contains(it.city) }
                .mapNotNull { it.city }
                .distinct()
            if (cities.isNotEmpty()) {
                val outcome = runCatching { housePriceRepository.refresh(cities) }
                    .onFailure { Log.w(TAG, "house price refresh failed", it) }
                    .getOrDefault(HouseRefreshResult(HouseRefreshOutcome.FAILED, null))
                if (outcome.outcome == HouseRefreshOutcome.FAILED) {
                    failedSources += SyncSource.HOUSE_PRICE
                } else if (outcome.latestMonth != null) {
                    // REFRESHED（拉到新数据）或 SKIPPED（用缓存）：把状态与最新月份带给 UI。
                    housePriceNote = HousePriceSyncNote(
                        status = if (outcome.outcome == HouseRefreshOutcome.REFRESHED)
                            HousePriceSyncStatus.REFRESHED else HousePriceSyncStatus.SKIPPED,
                        latestMonth = outcome.latestMonth,
                    )
                }
            }

            users.forEach { user ->
                val holdings = allHoldingsByUser[user.id].orEmpty()
                val quoteResult = quoteRepository.fetchPrices(holdings)
                failedSources += quoteResult.failedSources

                val priceChanges = mutableListOf<PriceChange>()
                val updated = holdings.map { holding ->
                    val newPrice = quoteResult.prices[holding.id]
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

                val alerts = AlertRules.depositMaturity(updated, now, stringResolver) +
                    AlertRules.priceMoves(priceChanges, now, stringResolver) +
                    AlertRules.marketMoves(user.id, indexChanges, MarketIndexes.displayNames(stringResolver), now, stringResolver) +
                    AlertRules.globalMoves(user.id, globalChanges, GlobalIndicators.displayNames(stringResolver), now, stringResolver) +
                    AlertRules.ipoAlerts(user.id, ipos, todayDate, now, stringResolver) +
                    subscriptionAlerts(user.id, now)
                alerts.forEach { alert ->
                    if (alertRepository.insertIfNew(alert)) alertNotifier.notify(alert)
                }
            }
            // 无条件保存本次汇率为「上次值」，供下一次同步做差值（首次同步 seed）。
            globalRatesStore.savePrevFx(rates)
        }

        val completedAt = nowProvider()
        syncStateStore.setLastSync(completedAt)
        val result = SyncResult(
            success = failedSources.isEmpty(),
            failedSources = failedSources.distinct().sorted(),
            completedAt = completedAt,
            manual = manual,
            housePriceNote = housePriceNote,
        )
        syncStateStore.setLastResult(result)
        result
    }.getOrElse {
        // 整体异常（如数据库错误）：仍记录一个失败结果上报，避免 UI 无反馈。
        val completedAt = nowProvider()
        val result = SyncResult(
            success = false,
            failedSources = listOf(SyncSource.FX, SyncSource.MARKET_INDEX, SyncSource.COMMODITY, SyncSource.IPO, SyncSource.HOUSE_PRICE, SyncSource.STOCK, SyncSource.CRYPTO, SyncSource.FUND).sorted(),
            completedAt = completedAt,
            manual = manual,
        )
        runCatching { syncStateStore.setLastResult(result) }
        Log.e(TAG, "sync failed entirely", it)
        result
    }

    /** 抓取市场指数，失败或空结果时重试一次并记录日志。 */
    private suspend fun fetchIndexChangesWithRetry(): Map<String, Double> {
        var changes = runCatching { marketIndexRemote.fetch() }
            .onFailure { Log.w(TAG, "marketIndex fetch failed", it) }
            .getOrDefault(emptyMap())
        if (changes.isEmpty()) {
            Log.i(TAG, "marketIndex empty, retrying once")
            changes = runCatching { marketIndexRemote.fetch() }
                .onFailure { Log.w(TAG, "marketIndex retry failed", it) }
                .getOrDefault(emptyMap())
        }
        return changes
    }

    /**
     * 组装国际行情涨跌%：汇率用「上次同步值 vs 当前」算（首次无 prev 则跳过），
     * 贵金属直接用 [commodityChanges]（当日 intraday，新浪官方昨收 vs 最新）。
     */
    private suspend fun buildGlobalChanges(rates: FxRates, commodityChanges: Map<String, Double>): Map<String, Double> {
        val prevFx = globalRatesStore.prevFx()
        return buildMap {
            prevFx?.let { prev ->
                if (prev.usdToCny > 0.0) {
                    put("usd_cny", (rates.usdToCny - prev.usdToCny) / prev.usdToCny * 100.0)
                }
                if (prev.hkdToCny > 0.0) {
                    put("hkd_cny", (rates.hkdToCny - prev.hkdToCny) / prev.hkdToCny * 100.0)
                }
            }
            putAll(commodityChanges)
        }
    }

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

    /**
     * 订阅提醒：先顺延已过期的续费日（错过多次一次补齐），再生成提前提醒。
     * 单独封装以便订阅侧失败不拖垮整次同步（保留其余提醒）。
     */
    private suspend fun subscriptionAlerts(userId: Long, now: Long) =
        runCatching {
            val subs = subscriptionRepository.getSubscriptionsSnapshot(userId)
            subs.forEach { sub ->
                SubscriptionRenewal.advance(sub, now)?.let { subscriptionRepository.updateSubscription(it) }
            }
            AlertRules.subscriptionRenewals(subs, now, stringResolver)
        }.getOrElse {
            Log.w(TAG, "subscription alerts failed", it)
            emptyList()
        }

    /** 便于测试覆盖的时间源。 */
    var nowProvider: () -> Long = { System.currentTimeMillis() }

    private companion object {
        const val TAG = "SyncManager"
        val DEFAULT_FX = FxRates()
    }
}
