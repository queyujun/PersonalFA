package com.yingjing.pfa.ui.theme

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/**
 * 应用可选主题配色。每套主题提供浅/深两套 ColorScheme + 品牌渐变，
 * 深浅模式由系统决定（[com.yingjing.pfa.ui.theme.PersonalFaTheme] 默认 isSystemInDarkTheme）。
 *
 * - [MORANDI] 莫兰迪：灰蓝 / 藕粉 / 灰绿，低饱和度、灰调，柔和雅致。
 * - [CELADON] 青瓷：青绿 / 墨青 / 米白，宋瓷釉色，清润古典。
 * - [INK] 墨韵：黛 / 烟灰 / 赭，水墨黛青，沉静内敛。
 *
 * [labelRes] 供设置页渲染选项名称。
 */
enum class AppTheme(val id: String, @StringRes val labelRes: Int) {
    MORANDI("morandi", R.string.theme_morandi),
    CELADON("celadon", R.string.theme_celadon),
    INK("ink", R.string.theme_ink);

    companion object {
        /** 按已保存的 id 查找；null 或未知 → 默认莫兰迪。 */
        fun fromId(id: String?): AppTheme =
            entries.firstOrNull { it.id == id } ?: MORANDI
    }
}
