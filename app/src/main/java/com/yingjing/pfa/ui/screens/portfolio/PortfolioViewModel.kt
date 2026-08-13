package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValue
import com.yingjing.pfa.domain.usecase.ObserveHoldingsUseCase
import com.yingjing.pfa.ui.format.MoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HoldingRow(
    val id: Long,
    val name: String,
    val subtitle: String,
    val valueText: String,
    val profitText: String?,
    val profitPositive: Boolean,
)

data class CategorySection(val title: String, val rows: List<HoldingRow>)

data class PortfolioUiState(val sections: List<CategorySection> = emptyList()) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

private val CATEGORY_ORDER = listOf(
    AssetCategory.REAL_ESTATE, AssetCategory.STOCK, AssetCategory.DEPOSIT,
    AssetCategory.GOLD, AssetCategory.BOND, AssetCategory.EQUITY,
    AssetCategory.CRYPTO, AssetCategory.LIABILITY,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    sessionManager: SessionManager,
    observeHoldings: ObserveHoldingsUseCase,
) : ViewModel() {

    val uiState: StateFlow<PortfolioUiState> = sessionManager.currentUserId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else observeHoldings(id) }
        .map { holdings -> buildState(holdings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    private fun buildState(holdings: List<Holding>): PortfolioUiState {
        val now = System.currentTimeMillis()
        val byCategory = holdings.groupBy { it.category }
        val sections = CATEGORY_ORDER.mapNotNull { category ->
            val items = byCategory[category] ?: return@mapNotNull null
            CategorySection(category.displayName, items.map { toRow(it, now) })
        }
        return PortfolioUiState(sections)
    }

    private fun toRow(holding: Holding, now: Long): HoldingRow {
        val value = HoldingValue.currentValue(holding, now)
        val profit = HoldingValue.profit(holding, now)
        return HoldingRow(
            id = holding.id,
            name = holding.name,
            subtitle = subtitle(holding),
            valueText = MoneyFormat.format(value, holding.currency),
            profitText = profit?.let { MoneyFormat.formatSigned(it, holding.currency) },
            profitPositive = (profit ?: 0.0) >= 0.0,
        )
    }

    private fun subtitle(h: Holding): String = when (h.type) {
        AssetType.A_SHARE -> "A股 ${h.symbol} · ${qty(h.quantity)}股"
        AssetType.HK_STOCK -> "港股 ${h.symbol} · ${qty(h.quantity)}股"
        AssetType.US_STOCK -> "美股 ${h.symbol} · ${qty(h.quantity)}股"
        AssetType.GOLD_ETF -> "黄金ETF ${h.symbol} · ${qty(h.quantity)}份"
        AssetType.BOND_ETF -> "国债ETF ${h.symbol} · ${qty(h.quantity)}份"
        AssetType.CRYPTO -> "${h.symbol} · ${qty(h.quantity)}"
        AssetType.ACCOUNT_CASH -> "账户现金"
        AssetType.DEPOSIT -> "年化 ${h.annualRatePercent ?: 0.0}% · 每日计息"
        AssetType.REAL_ESTATE ->
            listOfNotNull(h.city, h.areaSqm?.let { "${qty(it)}㎡" }).joinToString(" · ").ifBlank { "房产" }
        AssetType.EQUITY -> "未上市 · 手动估值"
        AssetType.LIABILITY -> h.liabilityType ?: "负债"
    }

    private fun qty(v: Double?): String {
        v ?: return "0"
        return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }
}
