package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.usecase.DeleteHoldingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HoldingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val holdingRepository: HoldingRepository,
    private val deleteHolding: DeleteHoldingUseCase,
    private val housePriceRepository: HousePriceRepository,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    private val _holding = MutableStateFlow<Holding?>(null)
    val holding: StateFlow<Holding?> = _holding.asStateFlow()

    /** 该房产城市缓存中的最新指数月份（"yyyy-MM"）；无城市 / 无缓存指数为 null。 */
    private val _latestIndexMonth = MutableStateFlow<String?>(null)
    val latestIndexMonth: StateFlow<String?> = _latestIndexMonth.asStateFlow()

    init {
        viewModelScope.launch {
            val holding = holdingRepository.getHolding(id)
            _holding.value = holding
            val city = holding?.city
            if (holding?.type == AssetType.REAL_ESTATE && city != null) {
                _latestIndexMonth.value = runCatching {
                    housePriceRepository.history(city).maxByOrNull { it.month }?.month
                }.getOrNull()
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteHolding(id)
            onDeleted()
        }
    }
}
