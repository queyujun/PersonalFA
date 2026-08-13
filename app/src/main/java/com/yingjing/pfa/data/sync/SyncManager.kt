package com.yingjing.pfa.data.sync

import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.SummarizePortfolio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日同步：刷新汇率 + 为所有用户的持仓抓取现价并写回 + 记录当日净值快照。
 *
 * 省流量：只抓实际持仓的标的，批量合并；失败静默降级（保留旧值）。
 */
@Singleton
class SyncManager @Inject constructor(
    private val userRepository: UserRepository,
    private val holdingRepository: HoldingRepository,
    private val quoteRepository: QuoteRepository,
    private val fxRepository: FxRepository,
    private val snapshotRepository: SnapshotRepository,
    private val syncStateStore: SyncStateStore,
) {
    /** 执行一次同步；返回是否成功。 */
    suspend fun sync(): Boolean = runCatching {
        fxRepository.refresh()
        val rates = fxRepository.current()

        userRepository.listUsers().forEach { user ->
            val holdings = holdingRepository.observeHoldingsSnapshot(user.id)
            val prices = quoteRepository.fetchPrices(holdings)
            val updated = holdings.map { holding ->
                prices[holding.id]?.let { price ->
                    val next = holding.copy(currentPrice = price)
                    holdingRepository.updateHolding(next)
                    next
                } ?: holding
            }
            val now = nowProvider()
            val summary = SummarizePortfolio(updated, rates, user.defaultCurrency, now)
            snapshotRepository.record(
                userId = user.id,
                currency = user.defaultCurrency,
                totalAssets = summary.totalAssets,
                totalLiabilities = summary.totalLiabilities,
                netWorth = summary.netWorth,
                nowMs = now,
            )
        }
        syncStateStore.setLastSync(nowProvider())
        true
    }.getOrDefault(false)

    /** 便于测试覆盖的时间源。 */
    var nowProvider: () -> Long = { System.currentTimeMillis() }
}
