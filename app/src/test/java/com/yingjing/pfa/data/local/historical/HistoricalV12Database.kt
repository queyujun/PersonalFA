// Reconstructed from git 395072d (identical local entities at 0999ccc^).
// Test-only historical source; package relocated, DAO accessors omitted, schema export enabled.
package com.yingjing.pfa.data.local.historical

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
    exportSchema = true,
)
abstract class HistoricalV12Database : RoomDatabase() {

    companion object {
        const val NAME = "pfa.db"
    }
}
