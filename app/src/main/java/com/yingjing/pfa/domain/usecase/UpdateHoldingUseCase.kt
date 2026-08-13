package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValidator
import com.yingjing.pfa.domain.repository.HoldingRepository
import javax.inject.Inject

/** 编辑持仓：先校验再更新。 */
class UpdateHoldingUseCase @Inject constructor(
    private val holdingRepository: HoldingRepository,
) {
    suspend operator fun invoke(holding: Holding): SaveHoldingResult {
        HoldingValidator.validate(holding)?.let { return SaveHoldingResult.Invalid(it) }
        holdingRepository.updateHolding(holding)
        return SaveHoldingResult.Success(holding.id)
    }
}
