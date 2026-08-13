package com.yingjing.pfa.di

import com.yingjing.pfa.data.repository.FxRepositoryImpl
import com.yingjing.pfa.data.repository.HoldingRepositoryImpl
import com.yingjing.pfa.data.repository.QuoteRepositoryImpl
import com.yingjing.pfa.data.repository.SnapshotRepositoryImpl
import com.yingjing.pfa.data.repository.UserRepositoryImpl
import com.yingjing.pfa.data.session.DataStoreSessionManager
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindSessionManager(impl: DataStoreSessionManager): SessionManager

    @Binds
    @Singleton
    abstract fun bindHoldingRepository(impl: HoldingRepositoryImpl): HoldingRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRepository(impl: QuoteRepositoryImpl): QuoteRepository

    @Binds
    @Singleton
    abstract fun bindFxRepository(impl: FxRepositoryImpl): FxRepository

    @Binds
    @Singleton
    abstract fun bindSnapshotRepository(impl: SnapshotRepositoryImpl): SnapshotRepository
}
