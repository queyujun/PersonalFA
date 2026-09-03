package com.yingjing.pfa.ui.screens.subscription

import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.SubscriptionRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {

    private val repository = FakeSubscriptionRepository()
    private val session = FakeSessionManager()
    private val users = FakeUserRepository()
    private val fx = FakeFxRepository(FxRates(usdToCny = 7.0, hkdToCny = 0.9))

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SubscriptionViewModel(session, repository, fx, users)

    private fun sub(
        id: Long,
        amount: Double,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        currency: Currency = Currency.CNY,
        nextRenewal: LocalDate = LocalDate.of(2026, 9, 10),
        active: Boolean = true,
    ) = Subscription(
        id = id, userId = 1, name = "sub$id", category = SubscriptionCategory.VIDEO,
        currency = currency, amount = amount, cycle = cycle,
        firstBillEpochMs = 0, nextRenewalEpochMs = Subscription.epochMsOf(nextRenewal),
        reminderDaysBefore = 3, active = active,
    )

    @Test
    fun emptyState_whenNotLoggedIn() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.subscriptions.isEmpty())
        assertNull(vm.uiState.value.summary)
    }

    @Test
    fun listAndSummary_flowFromRepository() = runTest {
        session.setCurrentUser(1)
        repository.upsert(sub(1, 30.0))
        repository.upsert(sub(2, 10.0, currency = Currency.USD, nextRenewal = LocalDate.of(2026, 9, 5)))
        advanceUntilIdle()

        val state = viewModel().uiState.first { it.summary != null }
        assertEquals(2, state.subscriptions.size)
        // 30 + 10×7 = 100 CNY/月
        assertEquals(100.0, state.summary!!.monthly, 1e-9)
        assertEquals(Currency.CNY, state.displayCurrency)
    }

    @Test
    fun displayRenewal_pastDate_shownAdvanced_inMemory() = runTest {
        // 库中续费日已过（同步未跑）→ 展示内存顺延，库值不变。
        session.setCurrentUser(1)
        repository.upsert(sub(1, 30.0, nextRenewal = LocalDate.of(2026, 1, 10)))
        advanceUntilIdle()

        val state = viewModel().uiState.first { it.summary != null }
        val shown = state.subscriptions.single()
        assertTrue(shown.nextRenewalEpochMs > Subscription.epochMsOf(LocalDate.of(2026, 9, 3)))
        // 库里仍是原值
        assertEquals(
            Subscription.epochMsOf(LocalDate.of(2026, 1, 10)),
            repository.items.single().nextRenewalEpochMs,
        )
    }

    @Test
    fun delete_removesSubscription() = runTest {
        session.setCurrentUser(1)
        repository.upsert(sub(1, 30.0))
        val vm = viewModel()
        advanceUntilIdle()
        vm.delete(1)
        advanceUntilIdle()
        assertTrue(repository.items.none { it.id == 1L })
    }

    /** 内存版订阅仓库（同包共享给表单 VM 测试）。 */
    class FakeSubscriptionRepository : SubscriptionRepository {
        val items = mutableListOf<Subscription>()
        val updates = mutableListOf<Subscription>()
        val deleted = mutableListOf<Long>()
        private val state = MutableStateFlow<List<Subscription>>(emptyList())

        init {
            publish()
        }

        fun upsert(sub: Subscription) {
            items.removeAll { it.id == sub.id }
            items += sub
            publish()
        }

        private fun publish() {
            state.value = items.sortedBy { it.nextRenewalEpochMs }
        }

        override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> = state

        override suspend fun getSubscriptionsSnapshot(userId: Long): List<Subscription> = items.toList()

        override suspend fun getSubscription(id: Long): Subscription? = items.firstOrNull { it.id == id }

        override suspend fun addSubscription(subscription: Subscription): Long {
            val id = (items.maxOfOrNull { it.id } ?: 0L) + 1
            val withId = subscription.copy(id = id)
            items += withId
            publish()
            return id
        }

        override suspend fun updateSubscription(subscription: Subscription) {
            updates += subscription
            items.removeAll { it.id == subscription.id }
            items += subscription
            publish()
        }

        override suspend fun deleteSubscription(id: Long) {
            deleted += id
            items.removeAll { it.id == id }
            publish()
        }
    }

    /** 内存版汇率仓库。 */
    class FakeFxRepository(private var current: FxRates) : FxRepository {
        override fun observeRates(): Flow<FxRates> = flowOf(current)
        override suspend fun current(): FxRates = current
        override suspend fun refresh(): FxRates = current
        override suspend fun save(rates: FxRates) { current = rates }
    }
}
