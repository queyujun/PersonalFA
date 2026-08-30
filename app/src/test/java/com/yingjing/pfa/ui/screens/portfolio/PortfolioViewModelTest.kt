package com.yingjing.pfa.ui.screens.portfolio

import app.cash.turbine.test
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.data.sync.LanguageStore
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.usecase.ObserveHoldingsUseCase
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeLanguageStore
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {

    private val repository = FakeHoldingRepository()
    private val session = FakeSessionManager()
    private val users = FakeUserRepository()
    private val collapseStore = PortfolioCollapseStore()
    private val languageStore = FakeLanguageStore()

    /**
     * 返回真实货币符号的 resolver：把 Currency.symbolRes 映射到符号文本，
     * 让 [defaultCurrencyChange_refreshesSectionTotalSymbol] 能断言符号随展示币种切换。
     * 其它 resId 回退到 FakeStringResolver 的 "res<id>"，便于区分但不绑 locale。
     *
     * 同时通过 [languageTag] 模拟不同 locale：资产页文本经 build() 预解析进 state，
     * 语言切换应触发 combine 重跑并按新 locale 重新解析。这里用 languageTag 前缀
     * 给每个字符串打标，断言「语言切换后文本已按新语言重渲染」。
     */
    private val symbolResolver = object : StringResolver {
        private val symbols = mapOf(
            Currency.CNY.symbolRes to "¥",
            Currency.USD.symbolRes to "US$",
            Currency.HKD.symbolRes to "HK$",
        )
        private fun prefix() = "[${languageStore.tag ?: "sys"}]"
        override fun get(resId: Int): String = symbols[resId] ?: "${prefix()}res$resId"
        override fun get(resId: Int, vararg args: Any): String = "${prefix()}${args.joinToString(" ")}"
    }

    private val fx = object : FxRepository {
        override fun observeRates() = flowOf(FxRates(usdToCny = 7.0, hkdToCny = 0.9))
        override suspend fun current() = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
        override suspend fun refresh() = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
        override suspend fun save(rates: FxRates) {}
    }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        PortfolioViewModel(
            session,
            ObserveHoldingsUseCase(repository),
            fx,
            users,
            collapseStore,
            symbolResolver,
            languageStore,
        )

    private fun stock(name: String) = Holding(
        userId = 1, type = AssetType.A_SHARE, name = name, currency = Currency.CNY,
        symbol = "600519", quantity = 100.0, costPrice = 1650.0, currentPrice = 1680.0,
    )

    @Test
    fun emptyState_whenNoHoldings() = runTest {
        session.setCurrentUser(1)
        viewModel().uiState.test {
            assertTrue(awaitItem().isEmpty)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun groupsHoldingsByCategory() = runTest {
        session.setCurrentUser(1)
        repository.addHolding(stock("茅台"))
        repository.addHolding(
            Holding(userId = 1, type = AssetType.REAL_ESTATE, name = "房子", currency = Currency.CNY, manualValue = 1_000_000.0),
        )
        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isEmpty) state = awaitItem()
            assertEquals(2, state.sections.size) // 房产 + 股票
            assertEquals(2, state.sections.sumOf { s -> s.subGroups.sumOf { it.rows.size } })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun toggleCategory_updatesExpandedSet() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        assertTrue(vm.expandedCategories.value.isEmpty()) // 初始全折叠
        vm.toggleCategory("STOCK")
        assertEquals(setOf("STOCK"), vm.expandedCategories.value)
        vm.toggleCategory("STOCK")
        assertTrue(vm.expandedCategories.value.isEmpty()) // 再次折叠
    }

    @Test
    fun toggleCategory_persistsAcrossViewModelInstances() = runTest {
        // 同一 store 注入两个 VM 实例 → 折叠状态应共享（模拟离开再进入资产页）
        session.setCurrentUser(1)
        viewModel().toggleCategory("DEPOSIT")
        val secondVm = viewModel()
        assertEquals(setOf("DEPOSIT"), secondVm.expandedCategories.value)
    }

    @Test
    fun defaultCurrencyChange_refreshesSectionTotalSymbol() = runTest {
        // 修复「恢复后币种符号不刷新」的响应式验证：
        // USD 计价的 crypto，展示币种为 CNY 时合计符号为 ¥；切到 USD 后应变为 US$。
        session.setCurrentUser(1)
        users.register("alex", "pw", Currency.CNY)
        repository.addHolding(
            Holding(
                userId = 1, type = AssetType.CRYPTO, name = "BTC", currency = Currency.USD,
                symbol = "BTC", quantity = 1.0, currentPrice = 63_709.0,
            ),
        )

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isEmpty) state = awaitItem()
            // 展示币种 CNY：63709 USD × 7.0 = ¥445,963（四舍五入取整）
            val cryptoSection = state.sections.first { it.category == AssetCategory.CRYPTO }
            assertTrue("CNY 合计应带 ¥ 符号，实际：${cryptoSection.totalText}", cryptoSection.totalText.startsWith("¥"))

            // 改默认货币为 USD → observeUser 重发 → 合计符号应变为 US$
            users.updateDefaultCurrency(1, Currency.USD)
            state = awaitItem()
            val usdSection = state.sections.first { it.category == AssetCategory.CRYPTO }
            assertTrue("USD 合计应带 US\$ 符号，实际：${usdSection.totalText}", usdSection.totalText.startsWith("US\$"))

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun languageChange_refreshesResolvedText() = runTest {
        // 回归「切换语言后资产页文字不刷新」：build() 把文本预解析进 state，
        // combine 上游须含 locale 信号，否则语言切换不重跑、旧语言文案残留。
        // 用 languageTag 给每段文本打前缀，断言切换后文本前缀随之改变。
        session.setCurrentUser(1)
        repository.addHolding(stock("茅台"))

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isEmpty) state = awaitItem()
            val before = state.sections.first { it.category == AssetCategory.STOCK }
            assertTrue("初始（跟随系统）文本应带 [sys] 前缀，实际：${before.title}", before.title.startsWith("[sys]"))

            languageStore.setLanguage("zh-CN")
            state = awaitItem()
            val afterZh = state.sections.first { it.category == AssetCategory.STOCK }
            assertTrue("切换到中文后文本应带 [zh-CN] 前缀，实际：${afterZh.title}", afterZh.title.startsWith("[zh-CN]"))

            languageStore.setLanguage("en")
            state = awaitItem()
            val afterEn = state.sections.first { it.category == AssetCategory.STOCK }
            assertTrue("切换到英文后文本应带 [en] 前缀，实际：${afterEn.title}", afterEn.title.startsWith("[en]"))

            cancelAndConsumeRemainingEvents()
        }
    }
}
