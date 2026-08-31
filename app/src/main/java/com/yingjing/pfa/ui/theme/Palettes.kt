package com.yingjing.pfa.ui.theme

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/**
 * 应用可选主题配色。每套主题提供浅/深两套 ColorScheme + 品牌渐变，
 * 深浅模式由系统决定（[com.yingjing.pfa.ui.theme.PersonalFaTheme] 默认 isSystemInDarkTheme）。
 *
 * 四套以紫 / 蓝 / 绿 / 橙为基调，色调饱和度经降一档处理以保素雅，
 * 彼此色相差大，切换时整体观感（含底导航、页面底色）变化明显：
 *
 * - [VIOLET] 紫晶：深紫罗兰主色 + 薰衣草辅，沉稳贵气。
 * - [BLUE] 霁蓝：霁青蓝主色 + 天青辅，冷静克制。
 * - [GREEN] 松石：松石绿主色 + 浅艾辅，清润克制。
 * - [ORANGE] 琥珀：琥珀橙主色 + 浅驼辅，温润克制。
 *
 * [labelRes] 供设置页渲染选项名称。
 */
enum class AppTheme(val id: String, @StringRes val labelRes: Int) {
    VIOLET("violet", R.string.theme_violet),
    BLUE("blue", R.string.theme_blue),
    GREEN("green", R.string.theme_green),
    ORANGE("orange", R.string.theme_orange);

    companion object {
        // 旧版 id（morandi/celadon/ink）已下线，映射到最接近的新主题，避免已存偏好被丢回默认。
        private val legacyAliases = mapOf(
            "morandi" to BLUE,
            "celadon" to GREEN,
            "ink" to VIOLET,
        )

        /** 按已保存的 id 查找；null 或未知 → 默认紫晶。旧 id 经 [legacyAliases] 映射。 */
        fun fromId(id: String?): AppTheme =
            entries.firstOrNull { it.id == id } ?: legacyAliases[id] ?: VIOLET
    }
}
