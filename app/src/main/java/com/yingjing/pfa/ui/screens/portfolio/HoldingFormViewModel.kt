package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val type: AssetType =
        AssetType.valueOf(savedStateHandle.get<String>("type") ?: AssetType.A_SHARE.name)
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
                _state.update { it.copy(isSubmitting = false, error = "未登录") }
                return@launch
            }
            val result = if (isEdit) updateHolding(buildHolding(userId)) else addHolding(buildHolding(userId))
            when (result) {
                is SaveHoldingResult.Success -> onSaved()
                is SaveHoldingResult.Invalid ->
                    _state.update { it.copy(isSubmitting = false, error = result.reason) }
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
    )

    private fun Double?.toEditText(): String {
        this ?: return ""
        return if (this % 1.0 == 0.0) toLong().toString() else toString()
    }

    private fun parseDate(text: String): Long? {
        if (text.isBlank()) return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .parse(text.trim())?.time
        }.getOrNull()
    }

    private fun formatDate(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(epochMs))
}
