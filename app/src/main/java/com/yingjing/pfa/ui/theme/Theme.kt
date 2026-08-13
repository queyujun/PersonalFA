package com.yingjing.pfa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BlueLightMode,
    onPrimary = Color.White,
    secondary = Cat2,
    background = PagePlaneLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Ink2Light,
    outline = OutlineLight,
    error = GainRed,
)

private val DarkColors = darkColorScheme(
    primary = BlueDarkMode,
    onPrimary = Color.White,
    secondary = Cat2,
    background = PagePlaneDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Ink2Dark,
    outline = OutlineDark,
    error = Color(0xFFE34948),
)

@Composable
fun PersonalFaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
