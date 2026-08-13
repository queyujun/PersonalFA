package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.usecase.DeleteHoldingUseCase
import com.yingjing.pfa.fakes.FakeHoldingRepository
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
        )
        var deleted = false
        vm.delete { deleted = true }
        advanceUntilIdle()
        assertTrue(deleted)
        assertNull(repository.getHolding(id))
    }
}
