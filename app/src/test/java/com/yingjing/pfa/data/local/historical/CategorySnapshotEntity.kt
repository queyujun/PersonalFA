// Reconstructed from git 395072d (identical local entities at 0999ccc^).
// Test-only historical source; package relocated, DAO accessors omitted, schema export enabled.
package com.yingjing.pfa.data.local.historical

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日各资产类别金额快照（按 用户 + 自然日 + 类别 去重）。金额以用户默认币种计。 */
@Entity(
    tableName = "category_snapshots",
    indices = [Index(value = ["userId", "dayEpochDay", "category"], unique = true)],
)
data class CategorySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val dayEpochDay: Long,
    val category: String,
    val amount: Double,
)
