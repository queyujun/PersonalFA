package com.yingjing.pfa.ui.screens.subscription

import androidx.lifecycle.SavedStateHandle
import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory
import com.yingjing.pfa.domain.repository.SubscriptionRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeStringResolver
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionFormViewModelTest {

    // FakeSubscriptionRepository 定义在同包 SubscriptionViewModelTest.kt 中（Kotlin 顶层/嵌套类同包可见）。
    private val repository = SubscriptionViewModelTest.FakeSubscriptionRepository()
    private val session = FakeSessionManager()
    private val users = FakeUserRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(subId: Long = -1L) = SubscriptionFormViewModel(
        savedStateHandle = SavedStateHandle(mapOf("subId" to subId.toString())),
        sessionManager = session,
        subscriptionRepository = repository,
        stringResolver = FakeStringResolver(),
    )

    private fun fillRequiredFields(vm: SubscriptionFormViewModel) {
        vm.onField { copy(name = "视频会员", amount = "30", firstBillDate = "2026-09-01") }
    }

    // ---- 校验 ----

    @Test
    fun submit_blankName_setsNameError() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.onField { copy(amount = "30", firstBillDate = "2026-09-01") }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(saved)
        assertTrue(repository.items.isEmpty())
    }

    @Test
    fun submit_invalidAmount_setsAmountError() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.onField { copy(name = "视频会员", amount = "-5", firstBillDate = "2026-09-01") }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(saved)
    }

    @Test
    fun submit_zeroAmount_rejected() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.onField { copy(name = "视频会员", amount = "0", firstBillDate = "2026-09-01") }
        vm.submit { }
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun submit_badDate_setsDateError() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.onField { copy(name = "视频会员", amount = "30", firstBillDate = "2026/09/01") }
        vm.submit { }
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun editField_clearsPreviousError() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.submit { }
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        vm.onField { copy(name = "视频会员") }
        assertNull(vm.state.value.error)
    }

    // ---- 新建落库 ----

    @Test
    fun submit_valid_addsSubscription_withRenewalAtFirstBill() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        vm.onField {
            copy(
                name = "  AI 助手  ", category = SubscriptionCategory.AI, amount = "20",
                currency = Currency.USD, cycle = BillingCycle.MONTHLY, firstBillDate = "2026-09-01",
                reminderDaysBefore = 7, paymentMethod = "信用卡", note = "全家桶",
            )
        }
        var saved = false
        vm.submit { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertNull(vm.state.value.error)
        assertEquals(1, repository.items.size)
        val sub = repository.items.single()
        assertEquals("AI 助手", sub.name) // trim
        assertEquals(SubscriptionCategory.AI, sub.category)
        assertEquals(20.0, sub.amount, 1e-9)
        assertEquals(Currency.USD, sub.currency)
        assertEquals(LocalDate.of(2026, 9, 1), Subscription.dateOf(sub.nextRenewalEpochMs))
        assertEquals(LocalDate.of(2026, 9, 1), Subscription.dateOf(sub.firstBillEpochMs))
        assertEquals(7, sub.reminderDaysBefore)
        assertEquals("信用卡", sub.paymentMethod)
        assertEquals("全家桶", sub.note)
    }

    @Test
    fun submit_notLoggedIn_setsError() = runTest {
        // 不 setCurrentUser → 未登录错误
        val vm = viewModel()
        fillRequiredFields(vm)
        vm.submit { }
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertTrue(repository.items.isEmpty())
    }

    // ---- 编辑态 ----

    @Test
    fun editMode_loadsExistingSubscription() = runTest {
        val existing = Subscription(
            id = 9, userId = 1, name = "音乐会员", category = SubscriptionCategory.MUSIC,
            currency = Currency.CNY, amount = 15.0, cycle = BillingCycle.MONTHLY,
            firstBillEpochMs = 1_700_000_000_000L, nextRenewalEpochMs = 1_700_100_000_000L,
            reminderDaysBefore = 1, paymentMethod = null, note = null, active = false,
        )
        repository.items += existing
        val vm = viewModel(subId = 9)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEdit)
        assertEquals("音乐会员", vm.state.value.name)
        assertEquals(15.0, vm.state.value.amount.toDoubleOrNull()!!, 1e-9)
        assertFalse(vm.state.value.active)
    }

    @Test
    fun editMode_update_preservesRenewalAndCreatedAt() = runTest {
        session.setCurrentUser(1)
        val existing = Subscription(
            id = 9, userId = 1, name = "音乐会员", category = SubscriptionCategory.MUSIC,
            currency = Currency.CNY, amount = 15.0, cycle = BillingCycle.MONTHLY,
            firstBillEpochMs = 1_700_000_000_000L, nextRenewalEpochMs = 1_700_100_000_000L,
            reminderDaysBefore = 3, createdAtEpochMs = 123L, updatedAtEpochMs = 123L,
        )
        repository.items += existing
        val vm = viewModel(subId = 9)
        advanceUntilIdle()

        vm.onField { copy(name = "音乐会员 Pro", amount = "18") }
        vm.submit { }
        advanceUntilIdle()

        assertEquals(1, repository.updates.size)
        val updated = repository.updates.single()
        assertEquals("音乐会员 Pro", updated.name)
        assertEquals(18.0, updated.amount, 1e-9)
        // 顺延日与创建时间不被表单覆盖
        assertEquals(existing.nextRenewalEpochMs, updated.nextRenewalEpochMs)
        assertEquals(123L, updated.createdAtEpochMs)
        // 不产生新增
        assertTrue(repository.items.none { it.id == 0L })
    }

    @Test
    fun editMode_delete_removesSubscription() = runTest {
        repository.items += Subscription(
            id = 9, userId = 1, name = "音乐会员", category = SubscriptionCategory.MUSIC,
            currency = Currency.CNY, amount = 15.0, cycle = BillingCycle.MONTHLY,
            firstBillEpochMs = 0, nextRenewalEpochMs = 0, reminderDaysBefore = 3,
        )
        val vm = viewModel(subId = 9)
        advanceUntilIdle()

        var saved = false
        vm.delete { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertTrue(repository.deleted.contains(9L))
    }

    @Test
    fun createMode_delete_isNoOp() = runTest {
        val vm = viewModel()
        var saved = false
        vm.delete { saved = true }
        advanceUntilIdle()
        assertFalse(saved)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun submitDoubleTap_guardedByIsSubmitting() = runTest {
        session.setCurrentUser(1)
        val vm = viewModel()
        fillRequiredFields(vm)
        vm.submit { }
        vm.submit { }
        advanceUntilIdle()
        // 只落库一次
        assertEquals(1, repository.items.size)
    }
}
