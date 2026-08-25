package com.yingjing.pfa.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 持仓宽表（覆盖全部资产类别；type 存 AssetType.name，currency 存 Currency.code）。 */
@Entity(
    tableName = "holdings",
    indices = [Index(value = ["userId"])],
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val type: String,
    val name: String,
    val currency: String,
    val quantity: Double?,
    val costPrice: Double?,
    val currentPrice: Double?,
    val symbol: String?,
    val manualValue: Double?,
    val city: String?,
    val areaSqm: Double?,
    val annualRatePercent: Double?,
    val startDateEpochMs: Long?,
    val maturityDateEpochMs: Long?,
    val depositType: String?,
    val sharePercent: Double?,
    val liabilityType: String?,
    val monthlyPayment: Double?,
    val repaymentDay: Int? = null,
    val lastRepaidYearMonth: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
