package com.yingjing.pfa.domain.usecase

/** 新增 / 编辑持仓的结果。 */
sealed interface SaveHoldingResult {
    data class Success(val id: Long) : SaveHoldingResult
    data class Invalid(val reason: String) : SaveHoldingResult
}
