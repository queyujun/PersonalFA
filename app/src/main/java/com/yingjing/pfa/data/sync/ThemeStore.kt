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

private val Context.themeDataStore by preferencesDataStore(name = "theme_state")

/**
 * 主题偏好：存储 [com.yingjing.pfa.ui.theme.AppTheme] 的 id 字符串（"morandi"/"celadon"/"ink"）。
 * null 表示未设置（首启），由消费方回退默认主题（莫兰迪）。
 *
 * 深浅模式三档：null = 跟随系统（默认，未写入即回退）、"light" = 强制浅色、"dark" = 强制深色，
 * 存同一 DataStore，由 [com.yingjing.pfa.MainActivity] 读取后覆盖 PersonalFaTheme 的 darkTheme。
 *
 * 抽成接口便于纯 JVM 测试（用 Fake，无需 DataStore/Context）与统一 DI 绑定，
 * 与项目内 Repository / LanguageStore 接口/实现分离约定一致。
 */
interface ThemeStore {
    /** 当前主题 id；null = 未设置（回退默认）。 */
    val themeId: Flow<String?>

    suspend fun setTheme(id: String)

    /** 深浅模式档位；null = 跟随系统。 */
    val darkMode: Flow<String?>

    suspend fun setDarkMode(mode: String?)
}

@Singleton
class ThemeStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemeStore {

    override val themeId: Flow<String?> = context.themeDataStore.data.map { it[THEME_ID] }

    override suspend fun setTheme(id: String) {
        context.themeDataStore.edit { prefs -> prefs[THEME_ID] = id }
    }

    override val darkMode: Flow<String?> = context.themeDataStore.data.map { it[DARK_MODE] }

    override suspend fun setDarkMode(mode: String?) {
        context.themeDataStore.edit { prefs ->
            if (mode == null) prefs.remove(DARK_MODE) else prefs[DARK_MODE] = mode
        }
    }

    private companion object {
        val THEME_ID = stringPreferencesKey("theme_id")
        val DARK_MODE = stringPreferencesKey("dark_mode")
    }
}
