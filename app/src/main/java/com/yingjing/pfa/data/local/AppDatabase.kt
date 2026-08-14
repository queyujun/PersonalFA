package com.yingjing.pfa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppMetaEntity::class, UserEntity::class, HoldingEntity::class, NetWorthSnapshotEntity::class, AlertEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appMetaDao(): AppMetaDao
    abstract fun userDao(): UserDao
    abstract fun holdingDao(): HoldingDao
    abstract fun netWorthSnapshotDao(): NetWorthSnapshotDao
    abstract fun alertDao(): AlertDao

    companion object {
        const val NAME = "pfa.db"
    }
}
