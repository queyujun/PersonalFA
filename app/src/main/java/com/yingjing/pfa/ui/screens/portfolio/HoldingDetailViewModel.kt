package com.yingjing.pfa.ui.screens.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.HoldingRepository
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
) : ViewModel() {

    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    private val _holding = MutableStateFlow<Holding?>(null)
    val holding: StateFlow<Holding?> = _holding.asStateFlow()

    init {
        viewModelScope.launch { _holding.value = holdingRepository.getHolding(id) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteHolding(id)
            onDeleted()
        }
    }
}
