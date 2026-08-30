package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.core.validation.ValidationFailure
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.usecase.AddHoldingUseCase
import com.yingjing.pfa.domain.usecase.SaveHoldingResult
import com.yingjing.pfa.domain.usecase.UpdateHoldingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HoldingFormState(
    val type: AssetType,
    val isEdit: Boolean = false,
    val name: String = "",
    val currency: Currency = Currency.CNY,
    val symbol: String = "",
    val quantity: String = "",
    val costPrice: String = "",
    val currentPrice: String = "",
    val manualValue: String = "",
    val city: String = "",
    val areaSqm: String = "",
    val annualRate: String = "",
    val depositType: String = "",
    val maturityDate: String = "",
    val sharePercent: String = "",
    val liabilityType: String = "",
    val monthlyPayment: String = "",
    val repaymentDay: String = "",
    val note: String = "",
    val autoEstimate: Boolean = false,
    // 场外基金子分类：true=中国大陆（在线抓取净值），false=其他（手录净值）
    val autoFetchNav: Boolean = false,
    val error: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class HoldingFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val holdingRepository: HoldingRepository,
    private val addHolding: AddHoldingUseCase,
    private val updateHolding: UpdateHoldingUseCase,
    private val stringResolver: StringResolver,
) : ViewModel() {

    private val type: AssetType =
        runCatching {
            AssetType.valueOf(savedStateHandle.get<String>("type") ?: AssetType.A_SHARE.name)
        }.getOrDefault(AssetType.A_SHARE)
    private val holdingId: Long = savedStateHandle.get<String>("holdingId")?.toLongOrNull() ?: -1L
    private val isEdit = holdingId > 0
    private var loaded: Holding? = null

    private val _state = MutableStateFlow(HoldingFormState(type = type, isEdit = isEdit))
    val state: StateFlow<HoldingFormState> = _state.asStateFlow()

    init {
        if (isEdit) {
            viewModelScope.launch {
                holdingRepository.getHolding(holdingId)?.let { holding ->
                    loaded = holding
                    _state.value = holding.toFormState()
                }
            }
        }
    }

    fun onField(transform: HoldingFormState.() -> HoldingFormState) =
        _state.update { it.transform().copy(error = null) }

    fun submit(onSaved: () -> Unit) {
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val userId = sessionManager.currentUserId.first()
            if (userId == null) {
                _state.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.err_not_logged_in)) }
                return@launch
            }
            val result = if (isEdit) updateHolding(buildHolding(userId)) else addHolding(buildHolding(userId))
            when (result) {
                is SaveHoldingResult.Success -> onSaved()
                is SaveHoldingResult.Invalid ->
                    _state.update { it.copy(isSubmitting = false, error = result.failure.resolve()) }
            }
        }
    }

    private fun buildHolding(userId: Long): Holding {
        val s = _state.value
        val now = System.currentTimeMillis()
        val startDate = when (type) {
            AssetType.DEPOSIT -> loaded?.startDateEpochMs ?: now
            else -> loaded?.startDateEpochMs
        }
        // 房产估算基准月：新建开启估算 → now；编辑时仅当「开启估算且原 baseDate 为空」
        // 或「manualValue 变化」时重置为 now，否则保留原值（避免改城市等顺手编辑丢历史调整）。
        val valueBaseDateEpochMs = if (type == AssetType.REAL_ESTATE && s.autoEstimate) {
            val loadedBase = loaded?.valueBaseDateEpochMs
            val manualChanged = s.manualValue.toDoubleOrNull() != loaded?.manualValue
            when {
                !isEdit -> now
                loadedBase == null -> now
                manualChanged -> now
                else -> loadedBase
            }
        } else {
            loaded?.valueBaseDateEpochMs
        }
        return Holding(
            id = if (isEdit) holdingId else 0,
            userId = userId,
            type = type,
            name = s.name.trim(),
            currency = s.currency,
            quantity = s.quantity.toDoubleOrNull(),
            costPrice = s.costPrice.toDoubleOrNull(),
            currentPrice = s.currentPrice.toDoubleOrNull(),
            symbol = s.symbol.trim().ifBlank { null },
            manualValue = s.manualValue.toDoubleOrNull(),
            city = s.city.trim().ifBlank { null },
            areaSqm = s.areaSqm.toDoubleOrNull(),
            annualRatePercent = s.annualRate.toDoubleOrNull(),
            startDateEpochMs = startDate,
            maturityDateEpochMs = parseDate(s.maturityDate),
            depositType = s.depositType.ifBlank { null },
            sharePercent = s.sharePercent.toDoubleOrNull(),
            liabilityType = s.liabilityType.ifBlank { null },
            monthlyPayment = s.monthlyPayment.toDoubleOrNull(),
            repaymentDay = s.repaymentDay.toIntOrNull()?.coerceIn(1, 31),
            lastRepaidYearMonth = loaded?.lastRepaidYearMonth,
            autoEstimate = if (type == AssetType.REAL_ESTATE) s.autoEstimate else null,
            valueBaseDateEpochMs = valueBaseDateEpochMs,
            estimatedValue = loaded?.estimatedValue,
            // 其他(MISC)备注；其余类型固定 null（向后兼容）
            note = if (type == AssetType.MISC) s.note.trim().ifBlank { null } else null,
            // 场外基金子分类：中国大陆(autoFetchNav=true) 在线抓取净值；其他/非 OTC → null。
            // 仅 true 才表示「中国大陆」，false（其他）归一为 null，与旧版 OTC 默认值一致。
            autoFetchNav = if (type == AssetType.OTC_FUND) s.autoFetchNav.takeIf { it } else null,
            createdAtEpochMs = loaded?.createdAtEpochMs ?: 0,
        )
    }

    private fun Holding.toFormState() = HoldingFormState(
        type = type,
        isEdit = true,
        name = name,
        currency = currency,
        symbol = symbol ?: "",
        quantity = quantity.toEditText(),
        costPrice = costPrice.toEditText(),
        currentPrice = currentPrice.toEditText(),
        manualValue = manualValue.toEditText(),
        city = city ?: "",
        areaSqm = areaSqm.toEditText(),
        annualRate = annualRatePercent.toEditText(),
        depositType = depositType ?: "",
        maturityDate = maturityDateEpochMs?.let { formatDate(it) } ?: "",
        sharePercent = sharePercent.toEditText(),
        liabilityType = liabilityType ?: "",
        monthlyPayment = monthlyPayment.toEditText(),
        repaymentDay = repaymentDay?.toString() ?: "",
        note = note ?: "",
        autoEstimate = autoEstimate == true,
        autoFetchNav = autoFetchNav == true,
    )

    private fun Double?.toEditText(): String {
        this ?: return ""
        return if (this % 1.0 == 0.0) toLong().toString() else toString()
    }

    private fun parseDate(text: String): Long? {
        if (text.isBlank()) return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .parse(text.trim())?.time
        }.getOrNull()
    }

    private fun formatDate(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(epochMs))

    /** 把 [ValidationFailure] 经 [stringResolver] 解析为当前 locale 文案。 */
    private fun ValidationFailure.resolve(): String =
        if (args.isEmpty()) stringResolver.get(resId)
        else stringResolver.get(resId, *args.toTypedArray())
}
