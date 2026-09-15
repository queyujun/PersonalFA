package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.sync.ThemeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 内存版 ThemeStore，纯 JVM 可跑；[setTheme]/[setDarkMode] 同步更新便于断言。 */
class FakeThemeStore(
    initialThemeId: String? = null,
    initialDarkMode: String? = null,
) : ThemeStore {
    private val _themeId = MutableStateFlow(initialThemeId)
    private val _darkMode = MutableStateFlow(initialDarkMode)

    override val themeId: Flow<String?> = _themeId.asStateFlow()
    override val darkMode: Flow<String?> = _darkMode.asStateFlow()

    val storedThemeId: String? get() = _themeId.value
    val storedDarkMode: String? get() = _darkMode.value

    override suspend fun setTheme(id: String) { _themeId.value = id }
    override suspend fun setDarkMode(mode: String?) { _darkMode.value = mode }
}
