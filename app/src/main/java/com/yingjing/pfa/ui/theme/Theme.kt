package com.yingjing.pfa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 四套主题的完整 Material3 ColorScheme（浅/深各一）。
 * 品牌主色随 [AppTheme] 变化；数据语义色（涨跌 / 分类）不在此，直接复用 [Color.kt] 顶层常量。
 *
 * 中性 / 表面色（background / surface / surfaceVariant / outline）各主题专属、染有该色调，
 * 使切换主题时整体观感（页面底色、卡片、底导航）变化明显，而非仅 primary 一处。
 * 每套取饱和度降一档，保持素雅克制。
 */

// ── 紫晶：深紫罗兰 / 薰衣草 ─────────────────────────────────────────────────
private val VioletLightColors = lightColorScheme(
    primary = Color(0xFF5B4B8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEF8),
    onPrimaryContainer = Color(0xFF1E1340),
    secondary = Color(0xFF9C8FB8),
    background = Color(0xFFF8F6FB),
    onBackground = Color(0xFF1B1A22),
    surface = Color(0xFFFCFBFE),
    onSurface = Color(0xFF1B1A22),
    surfaceVariant = Color(0xFFEEEAF6),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFFDBD4E8),
    outlineVariant = Color(0xFFF1EDF7),
    error = GainRed,
    onError = Color.White,
)

private val VioletDarkColors = darkColorScheme(
    primary = Color(0xFFCDBAFF),
    onPrimary = Color(0xFF321B63),
    primaryContainer = Color(0xFF4A3878),
    onPrimaryContainer = Color(0xFFE7DEF8),
    secondary = Color(0xFFB6A8D2),
    background = Color(0xFF131120),
    onBackground = Color(0xFFE8E4F1),
    surface = Color(0xFF1C1A2C),
    onSurface = Color(0xFFE8E4F1),
    surfaceVariant = Color(0xFF2E2A40),
    onSurfaceVariant = Color(0xFFC9C2D8),
    outline = Color(0xFF2E2A40),
    outlineVariant = Color(0xFF211F30),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

// ── 霁蓝：霁青蓝 / 天青 ─────────────────────────────────────────────────────
private val BlueLightColors = lightColorScheme(
    primary = Color(0xFF1F5C8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E6F7),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF86A6C2),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF191D22),
    surface = Color(0xFFFCFDFE),
    onSurface = Color(0xFF191D22),
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF434B54),
    outline = Color(0xFFD3DCE6),
    outlineVariant = Color(0xFFEFF3F7),
    error = GainRed,
    onError = Color.White,
)

private val BlueDarkColors = darkColorScheme(
    primary = Color(0xFF8FCBFB),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF1E4E73),
    onPrimaryContainer = Color(0xFFD2E6F7),
    secondary = Color(0xFFA1BFD8),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE4EAF0),
    surface = Color(0xFF181D22),
    onSurface = Color(0xFFE4EAF0),
    surfaceVariant = Color(0xFF272F37),
    onSurfaceVariant = Color(0xFFBFC8D2),
    outline = Color(0xFF272F37),
    outlineVariant = Color(0xFF1D2227),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

// ── 松石：松石绿 / 浅艾 ─────────────────────────────────────────────────────
private val GreenLightColors = lightColorScheme(
    primary = Color(0xFF1F6E5D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE3),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF7CA89A),
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF191F1D),
    surface = Color(0xFFFCFEFD),
    onSurface = Color(0xFF191F1D),
    surfaceVariant = Color(0xFFEAF2EE),
    onSurfaceVariant = Color(0xFF424D49),
    outline = Color(0xFFD2E0D8),
    outlineVariant = Color(0xFFEFF4F1),
    error = GainRed,
    onError = Color.White,
)

private val GreenDarkColors = darkColorScheme(
    primary = Color(0xFF8FD8C4),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF1E5B4D),
    onPrimaryContainer = Color(0xFFCDEDE3),
    secondary = Color(0xFFA0C9BC),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFE2EBE7),
    surface = Color(0xFF171F1C),
    onSurface = Color(0xFFE2EBE7),
    surfaceVariant = Color(0xFF25302C),
    onSurfaceVariant = Color(0xFFBCC9C3),
    outline = Color(0xFF25302C),
    outlineVariant = Color(0xFF1C2421),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

// ── 琥珀：琥珀橙 / 浅驼 ─────────────────────────────────────────────────────
private val OrangeLightColors = lightColorScheme(
    primary = Color(0xFF9A5520),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFADCC8),
    onPrimaryContainer = Color(0xFF381000),
    secondary = Color(0xFFB98E6E),
    background = Color(0xFFFBF7F4),
    onBackground = Color(0xFF221C18),
    surface = Color(0xFFFEFCFB),
    onSurface = Color(0xFF221C18),
    surfaceVariant = Color(0xFFF4EDE6),
    onSurfaceVariant = Color(0xFF52453C),
    outline = Color(0xFFE6D7CB),
    outlineVariant = Color(0xFFF5EFEA),
    error = GainRed,
    onError = Color.White,
)

private val OrangeDarkColors = darkColorScheme(
    primary = Color(0xFFF2B97A),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF7A4515),
    onPrimaryContainer = Color(0xFFFADCC8),
    secondary = Color(0xFFD4AE8B),
    background = Color(0xFF15110E),
    onBackground = Color(0xFFEFE6DF),
    surface = Color(0xFF1D1813),
    onSurface = Color(0xFFEFE6DF),
    surfaceVariant = Color(0xFF2F271F),
    onSurfaceVariant = Color(0xFFD0C3B8),
    outline = Color(0xFF2F271F),
    outlineVariant = Color(0xFF231D17),
    error = Color(0xFFE34948),
    onError = Color(0xFF1A0606),
)

/** 按主题 + 系统深浅解析完整 ColorScheme。 */
fun colorSchemeFor(theme: AppTheme, darkTheme: Boolean): ColorScheme = when (theme) {
    AppTheme.VIOLET -> if (darkTheme) VioletDarkColors else VioletLightColors
    AppTheme.BLUE -> if (darkTheme) BlueDarkColors else BlueLightColors
    AppTheme.GREEN -> if (darkTheme) GreenDarkColors else GreenLightColors
    AppTheme.ORANGE -> if (darkTheme) OrangeDarkColors else OrangeLightColors
}
