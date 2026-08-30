package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languageDataStore by preferencesDataStore(name = "language_state")

/**
 * 应用语言偏好：存储 BCP-47 语言标签（"zh-CN"/"zh-HK"/"en"），null 表示跟随系统。
 * 实际 locale 应用由 [com.yingjing.pfa.core.i18n.AppLanguage] + AppCompatDelegate 完成。
 *
 * 抽成接口便于纯 JVM 测试（用 Fake，无需 DataStore/Context）与统一 DI 绑定，
 * 与项目内 Repository 接口/实现分离约定一致。
 */
interface LanguageStore {
    /** 当前语言标签；null = 跟随系统。 */
    val languageTag: Flow<String?>

    suspend fun setLanguage(tag: String?)
}

@Singleton
class LanguageStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanguageStore {

    override val languageTag: Flow<String?> = context.languageDataStore.data.map { it[LANGUAGE_TAG] }

    override suspend fun setLanguage(tag: String?) {
        context.languageDataStore.edit { prefs ->
            if (tag == null) prefs.remove(LANGUAGE_TAG) else prefs[LANGUAGE_TAG] = tag
        }
    }

    private companion object {
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
    }
}
