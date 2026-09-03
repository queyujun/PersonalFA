package com.yingjing.pfa.ui.screens.subscription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.SubscriptionRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.SubscriptionCost
import com.yingjing.pfa.domain.usecase.SubscriptionRenewal
import com.yingjing.pfa.domain.usecase.SubscriptionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 列表筛选维度。 */
enum class SubscriptionFilter { ALL, ACTIVE, INACTIVE }

data class SubscriptionUiState(
    val displayCurrency: Currency = Currency.CNY,
    val summary: SubscriptionSummary? = null,
    /** 全量订阅（含停用），展示时已做内存续费顺延。 */
    val subscriptions: List<Subscription> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val fxRepository: FxRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<SubscriptionUiState> = sessionManager.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(SubscriptionUiState())
            } else {
                combine(
                    subscriptionRepository.observeSubscriptions(userId),
                    fxRepository.observeRates(),
                    userRepository.observeUser(userId).map { it?.defaultCurrency ?: Currency.CNY },
                ) { subs, rates, currency ->
                    val now = System.currentTimeMillis()
                    // 展示层内存顺延：同步尚未跑也能看到正确的下次续费日。
                    val displayed = subs.map { sub ->
                        sub.copy(nextRenewalEpochMs = SubscriptionRenewal.displayRenewal(sub, now).toEpochDay() * MS_PER_DAY)
                    }
                    SubscriptionUiState(
                        displayCurrency = currency,
                        summary = SubscriptionCost(displayed, rates, currency),
                        subscriptions = displayed,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionUiState())

    fun delete(id: Long) {
        viewModelScope.launch { subscriptionRepository.deleteSubscription(id) }
    }

    private companion object {
        const val MS_PER_DAY = 86_400_000L
    }
}

data class SubscriptionFormState(    val isEdit: Boolean = false,
    val name: String = "",
    val category: SubscriptionCategory = SubscriptionCategory.VIDEO,
    val amount: String = "",
    val currency: Currency = Currency.CNY,
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    /** 首扣日期文本 yyyy-MM-dd。 */
    val firstBillDate: String = "",
    val reminderDaysBefore: Int = 3,
    val paymentMethod: String = "",
    val note: String = "",
    val active: Boolean = true,
    val error: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class SubscriptionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val stringResolver: StringResolver,
) : ViewModel() {

    private val subId: Long = savedStateHandle.get<String>("subId")?.toLongOrNull() ?: -1L
    private val isEdit = subId > 0

    private val _state = MutableStateFlow(SubscriptionFormState(isEdit = isEdit))
    val state: StateFlow<SubscriptionFormState> = _state.asStateFlow()

    init {
        if (isEdit) {
            viewModelScope.launch {
                subscriptionRepository.getSubscription(subId)?.let { sub ->
                    _state.value = sub.toFormState()
                }
            }
        }
    }

    fun onField(transform: SubscriptionFormState.() -> SubscriptionFormState) =
        _state.update { it.transform().copy(error = null) }

    /** 编辑态删除订阅（仅 isEdit 有意义）。 */
    fun delete(onSaved: () -> Unit) {
        if (!isEdit) return
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(subId)
            onSaved()
        }
    }

    fun submit(onSaved: () -> Unit) {
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val userId = sessionManager.currentUserId.first()
            if (userId == null) {
                _state.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.err_not_logged_in)) }
                return@launch
            }
            val amount = _state.value.amount.toDoubleOrNull()
            val firstBill = parseDate(_state.value.firstBillDate)
            when {
                _state.value.name.isBlank() ->
                    _state.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.sub_err_name_required)) }
                amount == null || amount <= 0.0 ->
                    _state.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.sub_err_amount_invalid)) }
                firstBill == null ->
                    _state.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.sub_err_date_invalid)) }
                else -> {
                    val s = _state.value
                    val now = System.currentTimeMillis()
                    if (isEdit) {
                        val existing = subscriptionRepository.getSubscription(subId)
                        if (existing != null) {
                            subscriptionRepository.updateSubscription(
                                buildSubscription(userId, now, existing.id, existing.createdAtEpochMs, existing.nextRenewalEpochMs),
                            )
                        }
                    } else {
                        subscriptionRepository.addSubscription(buildSubscription(userId, now, 0, now, firstBillEpochMs(firstBill)))
                    }
                    onSaved()
                }
            }
        }
    }

    private fun buildSubscription(
        userId: Long,
        now: Long,
        id: Long,
        createdAt: Long,
        nextRenewalEpochMs: Long,
    ): Subscription {
        val s = _state.value
        return Subscription(
            id = id,
            userId = userId,
            name = s.name.trim(),
            category = s.category,
            note = s.note.trim().ifBlank { null },
            currency = s.currency,
            amount = s.amount.toDoubleOrNull() ?: 0.0,
            cycle = s.cycle,
            firstBillEpochMs = Subscription.epochMsOf(parseDate(s.firstBillDate) ?: Subscription.dateOf(now)),
            nextRenewalEpochMs = nextRenewalEpochMs,
            reminderDaysBefore = s.reminderDaysBefore,
            paymentMethod = s.paymentMethod.trim().ifBlank { null },
            active = s.active,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = now,
        )
    }

    /** 新订阅的续费日即首扣日；SubscriptionRenewal 会在同步时顺延到未来。 */
    private fun firstBillEpochMs(firstBill: LocalDate): Long = Subscription.epochMsOf(firstBill)

    private fun Subscription.toFormState() = SubscriptionFormState(
        isEdit = true,
        name = name,
        category = category,
        amount = amount.toEditText(),
        currency = currency,
        cycle = cycle,
        firstBillDate = Subscription.dateOf(firstBillEpochMs).toString(),
        reminderDaysBefore = reminderDaysBefore,
        paymentMethod = paymentMethod ?: "",
        note = note ?: "",
        active = active,
    )

    private fun Double.toEditText(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun parseDate(text: String): LocalDate? {
        if (text.isBlank()) return null
        return runCatching { LocalDate.parse(text.trim()) }.getOrNull()
    }
}
