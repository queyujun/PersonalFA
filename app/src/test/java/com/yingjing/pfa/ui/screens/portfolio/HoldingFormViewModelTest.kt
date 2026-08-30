package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.usecase.AddHoldingUseCase
import com.yingjing.pfa.domain.usecase.UpdateHoldingUseCase
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeStringResolver
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
import org.junit.Assert.assertNull
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
        stringResolver = FakeStringResolver(),
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

    @Test
    fun realEstate_newWithAutoEstimate_setsBaseDateToNow() = runTest {
        session.setCurrentUser(1)
        val before = System.currentTimeMillis()
        val vm = viewModel(type = "REAL_ESTATE")
        vm.onField {
            copy(name = "滨江一号", manualValue = "1000000", city = "北京", autoEstimate = true)
        }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        val savedHolding = repository.observeHoldings(1).first().single()
        val base = savedHolding.valueBaseDateEpochMs
        assertNotNull(base)
        assertTrue(base!! >= before)
        assertEquals(true, savedHolding.autoEstimate)
    }

    @Test
    fun realEstate_editCityOnly_preservesBaseDate() = runTest {
        session.setCurrentUser(1)
        val originalBase = 1_700_000_000_000L
        val id = repository.addHolding(
            Holding(
                userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
                currency = Currency.CNY, manualValue = 1_000_000.0, city = "上海",
                autoEstimate = true, valueBaseDateEpochMs = originalBase,
            ),
        )
        val vm = viewModel(type = "REAL_ESTATE", holdingId = id.toString())
        advanceUntilIdle()
        // 仅改城市，manualValue 不变 → 基准月应保留
        vm.onField { copy(city = "北京") }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        assertEquals(originalBase, repository.getHolding(id)!!.valueBaseDateEpochMs)
    }

    @Test
    fun realEstate_editManualValue_resetsBaseDate() = runTest {
        session.setCurrentUser(1)
        val originalBase = 1_700_000_000_000L
        val id = repository.addHolding(
            Holding(
                userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
                currency = Currency.CNY, manualValue = 1_000_000.0, city = "北京",
                autoEstimate = true, valueBaseDateEpochMs = originalBase,
            ),
        )
        val vm = viewModel(type = "REAL_ESTATE", holdingId = id.toString())
        advanceUntilIdle()
        val before = System.currentTimeMillis()
        // 改 manualValue → 基准月重置为 now
        vm.onField { copy(manualValue = "1200000") }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        val base = repository.getHolding(id)!!.valueBaseDateEpochMs
        assertNotNull(base)
        assertTrue(base!! >= before)
    }

    // —— 其他(MISC)：备注字段保存与编辑回填 ——

    @Test
    fun misc_newWithNote_savesNote() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel(type = "MISC")
        vm.onField { copy(name = "收藏品", manualValue = "5000", note = "老物件") }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        val savedHolding = repository.observeHoldings(1).first().single()
        assertEquals("收藏品", savedHolding.name)
        assertEquals(5_000.0, savedHolding.manualValue!!, 0.001)
        assertEquals("老物件", savedHolding.note)
    }

    @Test
    fun misc_blankNote_savesAsNull() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel(type = "MISC")
        vm.onField { copy(name = "杂项", manualValue = "100", note = "   ") }
        vm.submit { }
        advanceUntilIdle()
        val savedHolding = repository.observeHoldings(1).first().single()
        assertNull(savedHolding.note)
    }

    @Test
    fun misc_edit_prefillsNote() = runTest {
        session.setCurrentUser(1)
        val id = repository.addHolding(
            Holding(userId = 1, type = AssetType.MISC, name = "收藏品", currency = Currency.CNY, manualValue = 5_000.0, note = "老物件"),
        )
        val vm = viewModel(type = "MISC", holdingId = id.toString())
        advanceUntilIdle()
        assertEquals("收藏品", vm.state.value.name)
        assertEquals("老物件", vm.state.value.note)
    }

    // —— 场外基金子分类：中国大陆(autoFetchNav=true) 保存与编辑回填 —— //

    @Test
    fun otcFund_newWithAutoFetchNav_savesFlag() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel(type = "OTC_FUND")
        vm.onField {
            copy(name = "易方达蓝筹", symbol = "005827", quantity = "1000", costPrice = "1.50", autoFetchNav = true)
        }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        val savedHolding = repository.observeHoldings(1).first().single()
        assertEquals(true, savedHolding.autoFetchNav)
    }

    @Test
    fun otcFund_newWithManualRegion_savesNullFlag() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel(type = "OTC_FUND")
        vm.onField {
            copy(name = "某QDII", symbol = "000834", quantity = "100", costPrice = "1.20", autoFetchNav = false, currentPrice = "1.30")
        }
        vm.submit { }
        advanceUntilIdle()
        val savedHolding = repository.observeHoldings(1).first().single()
        assertNull(savedHolding.autoFetchNav)
    }

    @Test
    fun otcFund_edit_prefillsAutoFetchNav() = runTest {
        session.setCurrentUser(1)
        val id = repository.addHolding(
            Holding(userId = 1, type = AssetType.OTC_FUND, name = "易方达蓝筹", currency = Currency.CNY, symbol = "005827", quantity = 1_000.0, autoFetchNav = true),
        )
        val vm = viewModel(type = "OTC_FUND", holdingId = id.toString())
        advanceUntilIdle()
        assertEquals(true, vm.state.value.autoFetchNav)
    }
}
