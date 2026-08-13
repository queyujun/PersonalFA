package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.usecase.AddHoldingUseCase
import com.yingjing.pfa.domain.usecase.UpdateHoldingUseCase
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoldingFormViewModelTest {

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

    private fun viewModel(type: String = "A_SHARE", holdingId: String = "-1") = HoldingFormViewModel(
        savedStateHandle = SavedStateHandle(mapOf("type" to type, "holdingId" to holdingId)),
        sessionManager = session,
        holdingRepository = repository,
        addHolding = AddHoldingUseCase(repository),
        updateHolding = UpdateHoldingUseCase(repository),
    )

    @Test
    fun invalidInput_setsError_andDoesNotSave() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertFalse(saved)
        assertNotNull(vm.state.value.error)
        assertEquals(0, repository.observeHoldings(1).first().size)
    }

    @Test
    fun validInput_saves_andInvokesCallback() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.onField { copy(name = "贵州茅台", symbol = "600519", quantity = "100") }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        assertEquals(1, repository.observeHoldings(1).first().size)
    }

    @Test
    fun editMode_prefillsStateFromHolding() = runTest {
        session.setCurrentUser(1)
        val id = repository.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "贵州茅台", currency = Currency.CNY, symbol = "600519", quantity = 100.0),
        )
        val vm = viewModel(holdingId = id.toString())
        advanceUntilIdle()
        assertTrue(vm.state.value.isEdit)
        assertEquals("贵州茅台", vm.state.value.name)
    }
}
