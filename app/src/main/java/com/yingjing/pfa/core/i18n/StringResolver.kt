package com.yingjing.pfa.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 字符串资源解析器：让纯函数（非 Composable）也能按当前 locale 取本地化字符串。
 *
 * - Composable 内优先用 `stringResource()`。
 * - 纯函数 / ViewModel / Worker 注入本接口，调 [get]。
 * - 测试用 FakeStringResolver 提供固定字符串，不依赖 Android 资源。
 */
interface StringResolver {
    fun get(@StringRes resId: Int): String
    fun get(@StringRes resId: Int, vararg args: Any): String
}

@Singleton
class AppStringResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringResolver {

    // 解析时显式按 AppCompatDelegate 的 per-app locale 取串，而非直接用 ApplicationContext.getString。
    // 原因：setApplicationLocales 后 ApplicationContext 的 configuration 更新时机依赖
    // Activity 重建（异步），直接 getString 可能在「locale 已切换但配置尚未更新」的窗口内
    // 返回旧语言文案。getApplicationLocales 由 setApplicationLocales 同步设置，据此
    // createConfigurationContext 可立即反映最新 locale——这正是资产页 combine 重跑时所需。
    override fun get(resId: Int): String = localizedContext().getString(resId)

    override fun get(resId: Int, vararg args: Any): String = localizedContext().getString(resId, *args)

    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context // 跟随系统：用 ApplicationContext 默认 locale
        val config = Configuration(context.resources.configuration)
        config.setLocales(locales.unwrap() as android.os.LocaleList)
        return context.createConfigurationContext(config)
    }
}
