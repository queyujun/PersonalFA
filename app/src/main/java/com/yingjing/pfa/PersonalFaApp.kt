package com.yingjing.pfa

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yingjing.pfa.data.backup.BackupScheduler
import com.yingjing.pfa.data.sync.LanguageStore
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.data.sync.SyncStateStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalFaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var syncStateStore: SyncStateStore
    @Inject lateinit var languageStore: LanguageStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 应用已保存的语言偏好，确保冷启动 / Worker 内 context.getString 用正确 locale。
        CoroutineScope(Dispatchers.Default).launch {
            applySavedLanguage()
            // 按已保存的调度配置注册后台任务；KEEP 不扰动已存在的调度（配置变更时由设置页 REPLACE）。
            syncScheduler.schedule(
                intervalDays = syncStateStore.syncIntervalDays.first(),
                hour = syncStateStore.syncHour.first(),
                forceReplace = false,
            )
            if (syncStateStore.autoBackupEnabled.first()) {
                backupScheduler.schedule(
                    enabled = true,
                    intervalDays = syncStateStore.backupIntervalDays.first(),
                    hour = syncStateStore.backupHour.first(),
                    forceReplace = false,
                )
            }
        }
    }

    /** 读 DataStore 中已保存的语言标签并 apply 到 AppCompatDelegate（null = 跟随系统）。 */
    private suspend fun applySavedLanguage() {
        val tag = languageStore.languageTag.first()
        applyLanguage(tag)
    }

    companion object {
        /** 将语言标签应用到 AppCompatDelegate；null 或空 → 跟随系统。供 App 与 SettingsViewModel 共用。 */
        fun applyLanguage(tag: String?) {
            val locales = if (tag.isNullOrBlank()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
