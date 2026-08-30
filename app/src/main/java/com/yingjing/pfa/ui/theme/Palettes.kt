package com.yingjing.pfa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 三套主题的完整 Material3 ColorScheme（浅/深各一）。
 * 品牌主色随 [AppTheme] 变化；数据语义色（涨跌 / 分类）不在此，直接复用 [Color.kt] 顶层常量。
 * 中性 / 表面色为各主题专属，与莫兰迪/青瓷/墨韵的灰调相协调。
 */

// ── 莫兰迪 ───────────────────────────────────────────────────────────────
private val MorandiLightColors = lightColorScheme(
    primary = Color(0xFF6E8B9B),
    onPrimary = Color.White,
    secondary = Color(0xFFA3B5A0),
    background = Color(0xFFF8F8F6),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFCFCFB),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFF1F1EE),
    onSurfaceVariant = Color(0xFF52514E),
    outline = Color(0xFFE2E2DE),
    outlineVariant = Color(0xFFEDEDE9),
    error = GainRed,
    onError = Color.White,
)

private val MorandiDarkColors = darkColorScheme(
    primary = Color(0xFF8FA8B6),
    onPrimary = Color(0xFF0E1416),
    secondary = Color(0xFF93A592),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF5F4EF),
    surface = Color(0xFF1A1A19),
    onSurface = Color(0xFFF5F4EF),
    surfaceVariant = Color(0xFF2C2C2A),
    onSurfaceVariant = Color(0xFFC3C2B7),
    outline = Color(0xFF2C2C2A),
    outlineVariant = Color(0xFF1F1F1E),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

// ── 青瓷 ─────────────────────────────────────────────────────────────────
private val CeladonLightColors = lightColorScheme(
    primary = Color(0xFF4A6B66),
    onPrimary = Color.White,
    secondary = Color(0xFF7FA9A3),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF15201F),
    surface = Color(0xFFFCFCFA),
    onSurface = Color(0xFF15201F),
    surfaceVariant = Color(0xFFEFF1ED),
    onSurfaceVariant = Color(0xFF495450),
    outline = Color(0xFFE0E4DF),
    outlineVariant = Color(0xFFECEEEA),
    error = GainRed,
    onError = Color.White,
)

private val CeladonDarkColors = darkColorScheme(
    primary = Color(0xFF7FA9A3),
    onPrimary = Color(0xFF04110F),
    secondary = Color(0xFF6E948E),
    background = Color(0xFF080B0A),
    onBackground = Color(0xFFF3F5F2),
    surface = Color(0xFF101716),
    onSurface = Color(0xFFF3F5F2),
    surfaceVariant = Color(0xFF22302D),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF22302D),
    outlineVariant = Color(0xFF182220),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

// ── 墨韵 ─────────────────────────────────────────────────────────────────
private val InkLightColors = lightColorScheme(
    primary = Color(0xFF3A4A4A),
    onPrimary = Color.White,
    secondary = Color(0xFF6B6F70),
    background = Color(0xFFF8F8F9),
    onBackground = Color(0xFF181A1A),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF181A1A),
    surfaceVariant = Color(0xFFF1F1F2),
    onSurfaceVariant = Color(0xFF4E5152),
    outline = Color(0xFFE2E3E4),
    outlineVariant = Color(0xFFEDEEEE),
    error = GainRed,
    onError = Color.White,
)

private val InkDarkColors = darkColorScheme(
    primary = Color(0xFF7C8081),
    onPrimary = Color(0xFF0C1010),
    secondary = Color(0xFF7B7F80),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF171818),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF27292A),
    onSurfaceVariant = Color(0xFFC4C5C5),
    outline = Color(0xFF27292A),
    outlineVariant = Color(0xFF1C1E1F),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

/** 按主题 + 系统深浅解析完整 ColorScheme。 */
fun colorSchemeFor(theme: AppTheme, darkTheme: Boolean): ColorScheme = when (theme) {
    AppTheme.MORANDI -> if (darkTheme) MorandiDarkColors else MorandiLightColors
    AppTheme.CELADON -> if (darkTheme) CeladonDarkColors else CeladonLightColors
    AppTheme.INK -> if (darkTheme) InkDarkColors else InkLightColors
}
