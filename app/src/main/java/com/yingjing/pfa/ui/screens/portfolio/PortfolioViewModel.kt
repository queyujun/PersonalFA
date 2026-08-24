package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.ObserveHoldingsUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val observeHoldings: ObserveHoldingsUseCase,
    private val fxRepository: FxRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<PortfolioUiState> = sessionManager.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(PortfolioUiState())
            } else {
                combine(
                    observeHoldings(userId),
                    fxRepository.observeRates(),
                    userCurrencyFlow(userId),
                ) { holdings, rates, currency ->
                    PortfolioSectionsBuilder.build(holdings, rates, currency, System.currentTimeMillis())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    private fun userCurrencyFlow(userId: Long) =
        sessionManager.currentUserId.map { userRepository.getUser(userId)?.defaultCurrency ?: Currency.CNY }
}
