package com.morph.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ============================================================
// MORPH DESIGN SYSTEM: THEME
// Spec 2.2 / 2.3. Components read every color from here; no screen stores its own palette.
// ============================================================

private val LightColorScheme = lightColorScheme(
    primary = MorphPrimary,
    onPrimary = Color.White,
    primaryContainer = MorphPrimaryContainer,
    onPrimaryContainer = MorphPrimaryDark,
    inversePrimary = MorphPrimaryLight,

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
    surfaceTint = MorphPrimary,
    surfaceBright = MorphLightSurface,
    surfaceDim = MorphLightSurfaceContainerHighest,
    surfaceContainerLowest = MorphLightSurface,
    surfaceContainerLow = MorphLightSurfaceContainerLow,
    surfaceContainer = MorphLightSurfaceContainer,
    surfaceContainerHigh = MorphLightSurfaceContainerHigh,
    surfaceContainerHighest = MorphLightSurfaceContainerHighest,

    outline = MorphLightOutline,
    outlineVariant = MorphLightOutlineVariant,

    error = MorphError,
    onError = Color.White,
    errorContainer = MorphErrorContainerLight,
    onErrorContainer = Color(0xFF8B1B24),
)

private val DarkColorScheme = darkColorScheme(
    primary = MorphPrimaryLight,
    onPrimary = Color(0xFF1D0B4A),
    primaryContainer = MorphPrimaryContainerDark,
    onPrimaryContainer = Color(0xFFE6DDFF),
    inversePrimary = MorphPrimary,

    secondary = MorphSecondary,
    onSecondary = Color(0xFF1E0A40),
    secondaryContainer = Color(0xFF3D1D6B),
    onSecondaryContainer = Color(0xFFE5D4FF),

    tertiary = Color(0xFFF4ACDD),
    onTertiary = Color(0xFF3D0A28),
    tertiaryContainer = Color(0xFF5C1240),
    onTertiaryContainer = Color(0xFFFAD6EF),

    background = MorphDarkBackground,
    onBackground = MorphDarkTextPrimary,
    surface = MorphDarkSurface,
    onSurface = MorphDarkTextPrimary,
    surfaceVariant = MorphDarkSurfaceVariant,
    onSurfaceVariant = MorphDarkTextSecondary,
    surfaceTint = MorphPrimaryLight,
    surfaceBright = MorphDarkSurfaceContainerHighest,
    surfaceDim = MorphDarkBackground,
    surfaceContainerLowest = MorphDarkSurfaceContainerLowest,
    surfaceContainerLow = MorphDarkSurfaceContainerLow,
    surfaceContainer = MorphDarkSurfaceContainer,
    surfaceContainerHigh = MorphDarkSurfaceContainerHigh,
    surfaceContainerHighest = MorphDarkSurfaceContainerHighest,

    // Was pink at 80% alpha, which turned every divider bright pink in dark mode
    outline = MorphDarkOutline,
    outlineVariant = MorphDarkOutlineVariant,

    error = Color(0xFFEF7F87),
    onError = Color(0xFF3D0A0E),
    errorContainer = MorphErrorContainerDark,
    onErrorContainer = Color(0xFFFAB8BC),
)

/** Colors Material 3 has no slot for: the Instant bolt and success states. */
@Immutable
data class MorphExtendedColors(
    val isDark: Boolean,
    val instantBolt: Color,
    val instantContainer: Color,
    val onInstantContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Cards: white on light, one step above the background on dark. */
    val card: Color,
)

private val LightExtended = MorphExtendedColors(
    isDark = false,
    instantBolt = MorphInstantBolt,
    instantContainer = MorphInstantContainerLight,
    onInstantContainer = MorphOnInstantContainerLight,
    success = MorphSuccess,
    successContainer = MorphSuccessContainerLight,
    onSuccessContainer = MorphOnSuccessContainerLight,
    card = MorphLightSurface,
)

private val DarkExtended = MorphExtendedColors(
    isDark = true,
    instantBolt = MorphInstantBolt,
    instantContainer = MorphInstantContainerDark,
    onInstantContainer = MorphOnInstantContainerDark,
    success = Color(0xFF5FCB9A),
    successContainer = MorphSuccessContainerDark,
    onSuccessContainer = MorphOnSuccessContainerDark,
    card = MorphDarkSurface,
)

val LocalMorphColors = staticCompositionLocalOf { LightExtended }

/** True when the system "Remove animations" setting is on: skip looping motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

// ============================================================
// SHAPE TOKENS (spec 4.2)
// ============================================================
val MorphShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),   // chips
    medium = RoundedCornerShape(16.dp),  // file cards, buttons
    large = RoundedCornerShape(18.dp),   // feature cards
    extraLarge = RoundedCornerShape(28.dp) // sheets, dialogs
)

// ============================================================
// MORPH THEME
// themeOption: 0 = System, 1 = Light, 2 = Dark
// ============================================================
@Composable
fun MorphTheme(
    themeOption: Int = 0,
    language: String = "id",
    content: @Composable () -> Unit
) {
    val isDark = when (themeOption) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val strings = com.morph.app.core.AppStrings.get(language)

    CompositionLocalProvider(
        com.morph.app.core.LocalAppStrings provides strings,
        LocalMorphColors provides if (isDark) DarkExtended else LightExtended,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            shapes = MorphShapes,
            typography = MorphTypography,
            content = content
        )
    }
}
