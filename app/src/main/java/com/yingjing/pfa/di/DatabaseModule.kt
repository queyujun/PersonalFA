package com.yingjing.pfa.di

import android.content.Context
import androidx.room.Room
import com.yingjing.pfa.core.security.DatabaseKeyProvider
import com.yingjing.pfa.data.local.AlertDao
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.AppMetaDao
import com.yingjing.pfa.data.local.CategorySnapshotDao
import com.yingjing.pfa.data.local.HoldingDao
import com.yingjing.pfa.data.local.HousePriceDao
import com.yingjing.pfa.data.local.NetWorthSnapshotDao
import com.yingjing.pfa.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyProvider.getOrCreatePassphrase())
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(factory)
            // 开发期：schema 变更时销毁重建；发布前替换为正式迁移。
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideAppMetaDao(database: AppDatabase): AppMetaDao = database.appMetaDao()

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideHoldingDao(database: AppDatabase): HoldingDao = database.holdingDao()

    @Provides
    fun provideNetWorthSnapshotDao(database: AppDatabase): NetWorthSnapshotDao =
        database.netWorthSnapshotDao()

    @Provides
    fun provideCategorySnapshotDao(database: AppDatabase): CategorySnapshotDao =
        database.categorySnapshotDao()

    @Provides
    fun provideAlertDao(database: AppDatabase): AlertDao = database.alertDao()

    @Provides
    fun provideHousePriceDao(database: AppDatabase): HousePriceDao = database.housePriceDao()
}
