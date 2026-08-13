package com.yingjing.pfa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 通用键值元数据（P0 用于验证加密数据库读写；后续存 schema 版本、上次同步时间等）。 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
