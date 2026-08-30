package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.LanguageStore
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
    private val collapseStore: PortfolioCollapseStore,
    private val stringResolver: StringResolver,
    private val languageStore: LanguageStore,
) : ViewModel() {

    /** 资产页一级分类展开状态：集合中存在=展开，不存在=折叠。初始空集→首次进入全折叠。 */
    val expandedCategories: StateFlow<Set<String>> = collapseStore.expandedCategories

    /** 股票市场二级子类目展开状态。 */
    val expandedSubGroups: StateFlow<Set<String>> = collapseStore.expandedSubGroups

    fun toggleCategory(categoryKey: String) = collapseStore.toggleCategory(categoryKey)

    fun toggleSubGroup(subGroupKey: String) = collapseStore.toggleSubGroup(subGroupKey)

    val uiState: StateFlow<PortfolioUiState> = sessionManager.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(PortfolioUiState())
            } else {
                // 把 locale 信号（languageTag）纳入 combine 上游：资产页文本由 build() 经
                // stringResolver 预解析进 state，若语言切换不触发 combine 重跑，缓存的旧语言
                // 文案就不会刷新（其它页面在 @Composable 内用 stringResource 故正常）。
                // languageTag 在设置页写入 DataStore 后重发 → 这里重跑 → build() 用已更新的
                // locale 重新解析文本。
                combine(
                    observeHoldings(userId),
                    fxRepository.observeRates(),
                    userCurrencyFlow(userId),
                    languageStore.languageTag,
                ) { holdings, rates, currency, _ ->
                    PortfolioSectionsBuilder.build(holdings, rates, currency, System.currentTimeMillis(), stringResolver)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    private fun userCurrencyFlow(userId: Long) =
        userRepository.observeUser(userId).map { it?.defaultCurrency ?: Currency.CNY }
}
