package com.yingjing.pfa.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alerts",
    indices = [Index(value = ["userId", "dedupKey"], unique = true)],
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val category: String,
    val severity: String,
    val title: String,
    val body: String,
    val refHoldingId: Long?,
    val dedupKey: String,
    val createdAt: Long,
    val read: Boolean,
)
