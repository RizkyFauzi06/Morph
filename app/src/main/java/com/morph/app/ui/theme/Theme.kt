package com.morph.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = MorphIndigoLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = MorphVioletLight,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = MorphPinkLight,
    background = MorphDarkBg,
    onBackground = MorphDarkOnSurface,
    surface = MorphDarkSurface,
    onSurface = MorphDarkOnSurface,
    surfaceVariant = MorphDarkSurfaceVariant,
    onSurfaceVariant = MorphDarkOnSurfaceVariant,
    outline = MorphDarkBorder,
    error = MorphError,
    errorContainer = MorphErrorContainerDark,
    onError = Color.White,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColorScheme = lightColorScheme(
    primary = MorphIndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = MorphViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5F3FF),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = MorphPink,
    background = MorphLightBg,
    onBackground = MorphLightOnSurface,
    surface = MorphLightSurface,
    onSurface = MorphLightOnSurface,
    surfaceVariant = MorphLightSurfaceVariant,
    onSurfaceVariant = MorphLightOnSurfaceVariant,
    outline = MorphLightBorder,
    error = MorphError,
    errorContainer = MorphErrorContainerLight,
    onError = Color.White,
    onErrorContainer = Color(0xFF991B1B)
)

val MorphShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MorphTheme(
    themeOption: Int = 0, // 0: System, 1: Light, 2: Dark
    content: @Composable () -> Unit
) {
    val isDark = when (themeOption) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MorphShapes,
        content = content
    )
}
