package com.yingjing.pfa.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日净值快照（按 用户 + 自然日 去重）。金额以 currency 计。 */
@Entity(
    tableName = "net_worth_snapshots",
    indices = [Index(value = ["userId", "dayEpochDay"], unique = true)],
)
data class NetWorthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val dayEpochDay: Long,
    val currency: String,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val createdAt: Long,
)
