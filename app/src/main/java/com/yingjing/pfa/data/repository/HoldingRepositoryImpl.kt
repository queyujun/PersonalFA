package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.HoldingDao
import com.yingjing.pfa.data.local.HoldingEntity
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.HoldingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HoldingRepositoryImpl @Inject constructor(
    private val holdingDao: HoldingDao,
) : HoldingRepository {

    override fun observeHoldings(userId: Long): Flow<List<Holding>> =
        holdingDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun observeHoldingsSnapshot(userId: Long): List<Holding> =
        holdingDao.getByUser(userId).map { it.toDomain() }

    override suspend fun getHolding(id: Long): Holding? = holdingDao.findById(id)?.toDomain()

    override suspend fun addHolding(holding: Holding): Long {
        val now = System.currentTimeMillis()
        return holdingDao.insert(
            holding.copy(createdAtEpochMs = now, updatedAtEpochMs = now).toEntity(),
        )
    }

    override suspend fun updateHolding(holding: Holding) {
        holdingDao.update(holding.copy(updatedAtEpochMs = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteHolding(id: Long) = holdingDao.deleteById(id)
}

private fun HoldingEntity.toDomain() = Holding(
    id = id,
    userId = userId,
    type = AssetType.valueOf(type),
    name = name,
    currency = Currency.fromCode(currency),
    quantity = quantity,
    costPrice = costPrice,
    currentPrice = currentPrice,
    symbol = symbol,
    manualValue = manualValue,
    city = city,
    areaSqm = areaSqm,
    annualRatePercent = annualRatePercent,
    startDateEpochMs = startDateEpochMs,
    maturityDateEpochMs = maturityDateEpochMs,
    depositType = depositType,
    sharePercent = sharePercent,
    liabilityType = liabilityType,
    monthlyPayment = monthlyPayment,
    repaymentDay = repaymentDay,
    lastRepaidYearMonth = lastRepaidYearMonth,
    autoEstimate = autoEstimate,
    valueBaseDateEpochMs = valueBaseDateEpochMs,
    estimatedValue = estimatedValue,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
)

private fun Holding.toEntity() = HoldingEntity(
    id = id,
    userId = userId,
    type = type.name,
    name = name,
    currency = currency.code,
    quantity = quantity,
    costPrice = costPrice,
    currentPrice = currentPrice,
    symbol = symbol,
    manualValue = manualValue,
    city = city,
    areaSqm = areaSqm,
    annualRatePercent = annualRatePercent,
    startDateEpochMs = startDateEpochMs,
    maturityDateEpochMs = maturityDateEpochMs,
    depositType = depositType,
    sharePercent = sharePercent,
    liabilityType = liabilityType,
    monthlyPayment = monthlyPayment,
    repaymentDay = repaymentDay,
    lastRepaidYearMonth = lastRepaidYearMonth,
    autoEstimate = autoEstimate,
    valueBaseDateEpochMs = valueBaseDateEpochMs,
    estimatedValue = estimatedValue,
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
)
