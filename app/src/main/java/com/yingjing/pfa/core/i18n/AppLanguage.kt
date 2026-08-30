package com.yingjing.pfa.core.i18n

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/**
 * 应用可选语言。语言标签采用 BCP-47：
 * - [FOLLOW_SYSTEM] = null，跟随系统语言（首启默认）。
 * - [SIMPLIFIED_CHINESE] "zh-CN" 简体中文。
 * - [TRADITIONAL_HK] "zh-HK" 香港繁体。
 * - [ENGLISH] "en" 英文。
 *
 * [labelRes] 供设置页渲染选项名称（按当前 locale 显示）。
 */
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    FOLLOW_SYSTEM(tag = null, labelRes = R.string.lang_follow_system),
    SIMPLIFIED_CHINESE("zh-CN", R.string.lang_simplified_chinese),
    TRADITIONAL_HK("zh-HK", R.string.lang_traditional_hk),
    ENGLISH("en", R.string.lang_english);

    companion object {
        /** 按已保存的标签查找；null 或未知 → 跟随系统。 */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: FOLLOW_SYSTEM
    }
}
