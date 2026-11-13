package com.morph.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================
// MORPH DESIGN SYSTEM — THEME v1.0
// Sesuai MORPH_UI_UX_SPEC.md Section 2.2 & 2.3
// ============================================================

private val LightColorScheme = lightColorScheme(
    primary = MorphPrimary,
    onPrimary = Color.White,
    primaryContainer = MorphPrimaryContainer,
    onPrimaryContainer = MorphPrimaryDark,

    secondary = MorphSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0EAFF),
    onSecondaryContainer = Color(0xFF4A2D8A),

    tertiary = MorphAccentPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDE8F7),
    onTertiaryContainer = Color(0xFF8A1A6E),

    background = MorphLightBackground,
    onBackground = MorphLightTextPrimary,

    surface = MorphLightSurface,
    onSurface = MorphLightTextPrimary,

    surfaceVariant = MorphLightSurfaceVariant,
    onSurfaceVariant = MorphLightTextSecondary,

    outline = MorphLightOutline,
    outlineVariant = MorphLightOutline.copy(alpha = 0.6f),

    error = MorphError,
    onError = Color.White,
    errorContainer = MorphErrorContainerLight,
    onErrorContainer = Color(0xFF8B1B24),

    // Surface tonal elevation (Material3)
    surfaceTint = MorphPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = MorphPrimaryLight,       // A78BFA di dark
    onPrimary = Color(0xFF1A0A3D),
    primaryContainer = MorphPrimaryContainerDark,
    onPrimaryContainer = Color(0xFFD6C9FF),

    secondary = MorphSecondary,
    onSecondary = Color(0xFF1E0A40),
    secondaryContainer = Color(0xFF3D1D6B),
    onSecondaryContainer = Color(0xFFE5D4FF),

    tertiary = Color(0xFFF4ACDD),      // pink lebih terang untuk dark
    onTertiary = Color(0xFF3D0A28),
    tertiaryContainer = Color(0xFF5C1240),
    onTertiaryContainer = Color(0xFFFAD6EF),

    background = MorphDarkBackground,
    onBackground = MorphDarkTextPrimary,

    surface = MorphDarkSurface,
    onSurface = MorphDarkTextPrimary,

    surfaceVariant = MorphDarkSurfaceVariant,
    onSurfaceVariant = MorphDarkTextSecondary,

    outline = MorphDarkOutline,
    outlineVariant = MorphDarkOutline.copy(alpha = 0.7f),

    error = Color(0xFFEF7F87),
    onError = Color(0xFF3D0A0E),
    errorContainer = MorphErrorContainerDark,
    onErrorContainer = Color(0xFFFAB8BC),

    surfaceTint = MorphPrimaryLight,
)

// ============================================================
// SHAPE TOKENS
// Sesuai MORPH_UI_UX_SPEC.md Section 4.2
// ============================================================
val MorphShapes = Shapes(
    // Small: chip
    small = RoundedCornerShape(14.dp),
    // Medium: file card, button
    medium = RoundedCornerShape(16.dp),
    // Large: feature card
    large = RoundedCornerShape(18.dp),
    // ExtraLarge: bottom sheet, dialog
    extraLarge = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
)

// ============================================================
// MORPH THEME COMPOSABLE
// themeOption: 0 = System, 1 = Light, 2 = Dark
// ============================================================
@Composable
fun MorphTheme(
    themeOption: Int = 0,
    content: @Composable () -> Unit
) {
    val isDark = when (themeOption) {
        1    -> false
        2    -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MorphShapes,
        typography = MorphTypography,
        content = content
    )
}
