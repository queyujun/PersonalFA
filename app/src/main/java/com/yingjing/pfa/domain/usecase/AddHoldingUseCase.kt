package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValidator
import com.yingjing.pfa.domain.repository.HoldingRepository
import javax.inject.Inject

/** 新增持仓：先校验再落库。 */
class AddHoldingUseCase @Inject constructor(
    private val holdingRepository: HoldingRepository,
) {
    suspend operator fun invoke(holding: Holding): SaveHoldingResult {
        HoldingValidator.validate(holding)?.let { return SaveHoldingResult.Invalid(it) }
        return SaveHoldingResult.Success(holdingRepository.addHolding(holding))
    }
}
