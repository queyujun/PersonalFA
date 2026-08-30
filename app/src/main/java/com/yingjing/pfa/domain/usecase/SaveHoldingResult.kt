package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.core.validation.ValidationFailure

/** 新增 / 编辑持仓的结果。[Invalid] 携带未解析的 [ValidationFailure]。 */
sealed interface SaveHoldingResult {
    data class Success(val id: Long) : SaveHoldingResult
    data class Invalid(val failure: ValidationFailure) : SaveHoldingResult
}
