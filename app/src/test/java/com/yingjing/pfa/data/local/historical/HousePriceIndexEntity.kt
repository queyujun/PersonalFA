// Reconstructed from git 395072d (identical local entities at 0999ccc^).
// Test-only historical source; package relocated, DAO accessors omitted, schema export enabled.
package com.yingjing.pfa.data.local.historical

import androidx.room.Entity

/**
 * 70 城住宅价格指数缓存行。复合主键 (city, month) 支持 upsert(REPLACE)。
 * secondSequential 为二手住宅环比指数（估算采用）；其余字段供详情页透明展示。
 */
@Entity(tableName = "house_price_index", primaryKeys = ["city", "month"])
data class HousePriceIndexEntity(
    val city: String,
    val month: String, // yyyy-MM
    val newSequential: Double?,
    val newSame: Double?,
    val secondSequential: Double?,
    val secondSame: Double?,
)
