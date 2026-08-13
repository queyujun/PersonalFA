package com.yingjing.pfa.ui.screens.portfolio

import app.cash.turbine.test
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.usecase.ObserveHoldingsUseCase
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stock(name: String) = Holding(
        userId = 1, type = AssetType.A_SHARE, name = name, currency = Currency.CNY,
        symbol = "600519", quantity = 100.0, costPrice = 1650.0, currentPrice = 1680.0,
    )

    @Test
    fun emptyState_whenNoHoldings() = runTest {
        session.setCurrentUser(1)
        val viewModel = PortfolioViewModel(session, ObserveHoldingsUseCase(repository))
        viewModel.uiState.test {
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
        val viewModel = PortfolioViewModel(session, ObserveHoldingsUseCase(repository))
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isEmpty) state = awaitItem()
            assertEquals(2, state.sections.size) // 房产 + 股票
            assertEquals(2, state.sections.sumOf { it.rows.size })
            cancelAndConsumeRemainingEvents()
        }
    }
}
