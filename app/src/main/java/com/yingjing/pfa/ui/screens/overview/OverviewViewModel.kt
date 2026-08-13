package com.yingjing.pfa.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
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

data class OverviewUiState(
    val displayCurrency: Currency = Currency.CNY,
    val summary: PortfolioSummary? = null,
    val trend: List<Double> = emptyList(),
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
                ) { holdings, rates, currency, snapshots ->
                    OverviewUiState(
                        displayCurrency = currency,
                        summary = SummarizePortfolio(holdings, rates, currency, System.currentTimeMillis()),
                        trend = snapshots.map { it.netWorth },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private fun userCurrencyFlow(userId: Long) =
        sessionManager.currentUserId.map { userRepository.getUser(userId)?.defaultCurrency ?: Currency.CNY }
}
