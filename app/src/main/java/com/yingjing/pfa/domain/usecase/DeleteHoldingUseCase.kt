package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.repository.HoldingRepository
import javax.inject.Inject

/** 删除持仓。 */
class DeleteHoldingUseCase @Inject constructor(
    private val holdingRepository: HoldingRepository,
) {
    suspend operator fun invoke(id: Long) = holdingRepository.deleteHolding(id)
}
