package com.yingjing.pfa.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val defaultCurrency: String,
    val createdAt: Long,
    val nickname: String? = null,
    val gender: String? = null,
    val age: Int? = null,
)
