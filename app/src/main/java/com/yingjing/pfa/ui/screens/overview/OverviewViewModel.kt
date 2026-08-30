package com.yingjing.pfa.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.PortfolioSummary
import com.yingjing.pfa.domain.usecase.SummarizePortfolio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 一个资产类别在堆叠面积图上的每日序列（[values] 与 [OverviewStackedTrend.epochDays] 等长）。 */
data class StackedTrendSeries(
    val category: AssetCategory,
    val values: List<Double>,
)

/** 堆叠面积走势数据：共同时间轴 + 各类别每日金额（缺失日按 0，不贡献）。 */
data class OverviewStackedTrend(
    val epochDays: List<Long> = emptyList(),
    val series: List<StackedTrendSeries> = emptyList(),
)

data class OverviewUiState(
    val displayCurrency: Currency = Currency.CNY,
    val summary: PortfolioSummary? = null,
    val trend: List<Double> = emptyList(),
    val stackedTrend: OverviewStackedTrend = OverviewStackedTrend(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val holdingRepository: HoldingRepository,
    private val fxRepository: FxRepository,
    private val snapshotRepository: SnapshotRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> = sessionManager.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(OverviewUiState())
            } else {
                combine(
                    holdingRepository.observeHoldings(userId),
                    fxRepository.observeRates(),
                    userCurrencyFlow(userId),
                    snapshotRepository.observe(userId),
                    snapshotRepository.observeCategories(userId),
                ) { holdings, rates, currency, snapshots, categoryPoints ->
                    OverviewUiState(
                        displayCurrency = currency,
                        summary = SummarizePortfolio(holdings, rates, currency, System.currentTimeMillis()),
                        trend = snapshots.map { it.netWorth },
                        stackedTrend = buildStackedTrend(snapshots.map { it.epochDay }, categoryPoints),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private fun userCurrencyFlow(userId: Long) =
        userRepository.observeUser(userId).map { it?.defaultCurrency ?: Currency.CNY }
}

/**
 * 把「净值快照的每日时间轴」与「每日每类别金额」对齐为堆叠面积图数据（纯函数，可测）。
 *
 * - 时间轴取净值快照的 epochDay 序列（[netWorthDays] 必须已升序去重）。
 * - 仅保留资产类（[AssetCategory.isLiability] 为 false），负债不参与堆叠（避免负值拉低总高）。
 * - 某类别在某日无快照 → 该格按 0（不贡献），保证各序列等长。
 * - **排序**：按「最近一次非零金额」降序，当前规模大的类别在底部（自底而上 大→小）。
 *   用最近非零值而非严格末日值，抗单日快照缺失导致的排序错位。
 */
internal fun buildStackedTrend(
    netWorthDays: List<Long>,
    categoryPoints: List<CategoryPoint>,
): OverviewStackedTrend {
    if (netWorthDays.size < 2) return OverviewStackedTrend()

    val assetCategories = AssetCategory.entries.filter { !it.isLiability }
    val series = assetCategories.map { category ->
        val byDay = categoryPoints
            .filter { it.category == category.name }
            .associate { it.epochDay to it.amount }
        StackedTrendSeries(
            category = category,
            values = netWorthDays.map { day -> byDay[day] ?: 0.0 },
        )
    }
    // 自底而上：当前规模大的在底部（series 首项画在底层）。
    val sorted = series.sortedByDescending { it.values.lastOrNull { v -> v > 0.0 } ?: 0.0 }
    return OverviewStackedTrend(epochDays = netWorthDays, series = sorted)
}
