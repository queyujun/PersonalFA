package com.yingjing.pfa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppMetaEntity::class,
        UserEntity::class,
        HoldingEntity::class,
        NetWorthSnapshotEntity::class,
        CategorySnapshotEntity::class,
        AlertEntity::class,
        HousePriceIndexEntity::class,
        SubscriptionEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appMetaDao(): AppMetaDao
    abstract fun userDao(): UserDao
    abstract fun holdingDao(): HoldingDao
    abstract fun netWorthSnapshotDao(): NetWorthSnapshotDao
    abstract fun categorySnapshotDao(): CategorySnapshotDao
    abstract fun alertDao(): AlertDao
    abstract fun housePriceDao(): HousePriceDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val NAME = "pfa.db"
    }
}
