package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.HoldingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 观察某用户的全部持仓（响应式）。 */
class ObserveHoldingsUseCase @Inject constructor(
    private val holdingRepository: HoldingRepository,
) {
    operator fun invoke(userId: Long): Flow<List<Holding>> =
        holdingRepository.observeHoldings(userId)
}
