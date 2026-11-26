package com.yingjing.pfa.ui.screens.sentiment

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.PriceSeriesId
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.repository.SentimentRefreshOutcome
import com.yingjing.pfa.domain.repository.SentimentRefreshResult
import com.yingjing.pfa.domain.usecase.FearRange
import com.yingjing.pfa.fakes.FakeMarketSentimentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FearIndexViewModelsTest {

    private val repo = FakeMarketSentimentRepository()
    private val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun daily(count: Int, value: (Int) -> Double) =
        (0 until count).map { SeriesPoint(today - (count - 1 - it), value(it)) }

    @Test
    fun `overview state reflects cached series and failed codes`() = runTest {
        repo.seed(FearIndexId.VIX.code, daily(30) { 15.0 + it })
        repo.seed(FearIndexId.CRYPTO_FNG.code, daily(30) { 60.0 - it })
        repo.syncState.value = repo.syncState.value.copy(failedCodes = setOf(FearIndexId.VIX.code))
        val vm = FearIndexViewModel(repo)
        vm.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals(listOf(FearIndexId.VIX, FearIndexId.CRYPTO_FNG), state.cards.map { it.summary.id })
            assertTrue(state.cards.first().stale)
            assertEquals(2, state.composite!!.used)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overview only subscribes to recent window`() = runTest {
        val old = SeriesPoint(today - 2_000, 99.0)
        repo.seed(FearIndexId.VIX.code, listOf(old) + daily(5) { 20.0 })
        val vm = FearIndexViewModel(repo)
        vm.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals(5, state.cards.single().summary.oneYear.count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshIfStale is silent and refreshNow forces and emits result`() = runTest {
        val vm = FearIndexViewModel(repo)
        vm.refreshIfStale()
        assertEquals(listOf(false), repo.refreshCalls)
        repo.nextResult = SentimentRefreshResult(SentimentRefreshOutcome.PARTIAL, setOf(PriceSeriesId.BTC.code))
        vm.refreshEvents.test {
            vm.refreshNow()
            assertEquals(SentimentRefreshOutcome.PARTIAL, awaitItem().outcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(false, true), repo.refreshCalls)
    }

    @Test
    fun `detail loads index with overlay and switches range and overlay`() = runTest {
        repo.seed(FearIndexId.VIX.code, daily(900) { 15.0 + it % 10 })
        repo.seed(PriceSeriesId.SPX.code, daily(900) { 6_000.0 - it % 10 })
        val vm = FearIndexDetailViewModel(SavedStateHandle(mapOf(FearIndexDetailViewModel.ARG_ID to FearIndexId.VIX.code)), repo)
        vm.uiState.test {
            var s = awaitItem()
            while (s.content == null) s = awaitItem()
            assertEquals(FearRange.Y1, s.range)
            assertTrue(s.showOverlay)
            assertTrue(s.content!!.chart!!.overlay.isNotEmpty())

            vm.selectRange(FearRange.Y2)
            while (s.range != FearRange.Y2) s = awaitItem()
            assertEquals(731, s.content!!.rangeStats!!.count)

            vm.toggleOverlay()
            while (s.showOverlay) s = awaitItem()
            assertFalse(s.showOverlay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `vvix detail overlays vix`() = runTest {
        repo.seed(FearIndexId.VVIX.code, daily(60) { 100.0 + it })
        repo.seed(FearIndexId.VIX.code, daily(60) { 20.0 + it })
        val vm = FearIndexDetailViewModel(SavedStateHandle(mapOf(FearIndexDetailViewModel.ARG_ID to FearIndexId.VVIX.code)), repo)
        val content = vm.uiState.first { it.content != null }.content!!
        assertEquals(60, content.chart!!.overlay.size)
    }

    @Test
    fun `unknown detail id yields loaded empty state`() = runTest {
        val vm = FearIndexDetailViewModel(SavedStateHandle(mapOf(FearIndexDetailViewModel.ARG_ID to "nope")), repo)
        val s = vm.uiState.value
        assertTrue(s.loaded)
        assertNull(s.id)
        assertNull(s.content)
    }
}
