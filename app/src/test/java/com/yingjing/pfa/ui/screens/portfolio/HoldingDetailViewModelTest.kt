package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.usecase.DeleteHoldingUseCase
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeHousePriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoldingDetailViewModelTest {

    private val repository = FakeHoldingRepository()
    private val housePriceRepository = FakeHousePriceRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun house() = Holding(
        userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号", currency = Currency.CNY, manualValue = 1_750_000.0,
    )

    @Test
    fun loadsHoldingById() = runTest {
        val id = repository.addHolding(house())
        val vm = HoldingDetailViewModel(
            SavedStateHandle(mapOf("id" to id.toString())),
            repository,
            DeleteHoldingUseCase(repository),
            housePriceRepository,
        )
        advanceUntilIdle()
        assertEquals("滨江一号", vm.holding.value?.name)
    }

    @Test
    fun delete_removesHolding_andInvokesCallback() = runTest {
        val id = repository.addHolding(house())
        val vm = HoldingDetailViewModel(
            SavedStateHandle(mapOf("id" to id.toString())),
            repository,
            DeleteHoldingUseCase(repository),
            housePriceRepository,
        )
        var deleted = false
        vm.delete { deleted = true }
        advanceUntilIdle()
        assertTrue(deleted)
        assertNull(repository.getHolding(id))
    }

    @Test
    fun realEstate_withCity_loadsLatestIndexMonth() = runTest {
        val id = repository.addHolding(house().copy(city = "北京"))
        housePriceRepository.seed(
            "北京",
            listOf(
                HousePricePoint("北京", "2026-05", 100.1, 99.8, 99.9, 98.7),
                HousePricePoint("北京", "2026-07", 100.2, 99.6, 100.0, 98.1),
                HousePricePoint("北京", "2026-06", 99.8, 99.5, 99.7, 98.4),
            ),
        )
        val vm = HoldingDetailViewModel(
            SavedStateHandle(mapOf("id" to id.toString())),
            repository,
            DeleteHoldingUseCase(repository),
            housePriceRepository,
        )
        advanceUntilIdle()
        assertEquals("2026-07", vm.latestIndexMonth.value)
    }

    @Test
    fun realEstate_withoutCachedIndex_latestIndexMonthIsNull() = runTest {
        val id = repository.addHolding(house().copy(city = "北京"))
        val vm = HoldingDetailViewModel(
            SavedStateHandle(mapOf("id" to id.toString())),
            repository,
            DeleteHoldingUseCase(repository),
            housePriceRepository,
        )
        advanceUntilIdle()
        assertNull(vm.latestIndexMonth.value)
    }

    @Test
    fun nonRealEstate_doesNotLoadIndexMonth() = runTest {
        val id = repository.addHolding(
            Holding(userId = 1, type = AssetType.DEPOSIT, name = "定期", currency = Currency.CNY, manualValue = 100.0, annualRatePercent = 2.0),
        )
        val vm = HoldingDetailViewModel(
            SavedStateHandle(mapOf("id" to id.toString())),
            repository,
            DeleteHoldingUseCase(repository),
            housePriceRepository,
        )
        advanceUntilIdle()
        assertNull(vm.latestIndexMonth.value)
    }
}
