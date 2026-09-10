package com.yingjing.pfa.di

import com.yingjing.pfa.core.i18n.AppStringResolver
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.core.security.AiSecretStore
import com.yingjing.pfa.core.security.DatabaseAiSecretStore
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.data.ai.AiSettingsStoreImpl
import com.yingjing.pfa.data.notification.AndroidAlertNotifier
import com.yingjing.pfa.data.repository.AiReportRecordRepositoryImpl
import com.yingjing.pfa.data.repository.AlertRepositoryImpl
import com.yingjing.pfa.data.repository.FxRepositoryImpl
import com.yingjing.pfa.data.repository.HoldingRepositoryImpl
import com.yingjing.pfa.data.repository.HousePriceRepositoryImpl
import com.yingjing.pfa.data.repository.QuoteRepositoryImpl
import com.yingjing.pfa.data.repository.SnapshotRepositoryImpl
import com.yingjing.pfa.data.repository.SubscriptionRepositoryImpl
import com.yingjing.pfa.data.repository.UserRepositoryImpl
import com.yingjing.pfa.data.session.DataStoreSessionManager
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.LanguageStore
import com.yingjing.pfa.data.sync.LanguageStoreImpl
import com.yingjing.pfa.data.sync.ThemeStore
import com.yingjing.pfa.data.sync.ThemeStoreImpl
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.repository.AiReportRecordRepository
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.SubscriptionRepository
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

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindAlertNotifier(impl: AndroidAlertNotifier): AlertNotifier

    @Binds
    @Singleton
    abstract fun bindHousePriceRepository(impl: HousePriceRepositoryImpl): HousePriceRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindStringResolver(impl: AppStringResolver): StringResolver

    @Binds
    @Singleton
    abstract fun bindLanguageStore(impl: LanguageStoreImpl): LanguageStore

    @Binds
    @Singleton
    abstract fun bindThemeStore(impl: ThemeStoreImpl): ThemeStore

    @Binds
    @Singleton
    abstract fun bindAiReportRecordRepository(
        impl: AiReportRecordRepositoryImpl,
    ): AiReportRecordRepository

    @Binds
    @Singleton
    abstract fun bindAiSettingsStore(impl: AiSettingsStoreImpl): AiSettingsStore

    @Binds
    @Singleton
    abstract fun bindAiSecretStore(impl: DatabaseAiSecretStore): AiSecretStore
}
