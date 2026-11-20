package com.morph.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// MORPH DESIGN SYSTEM: COLOR TOKENS
// Based on MORPH_UI_UX_SPEC.md section 2.2. One accent (violet) for actions and selection;
// yellow is reserved for the Instant bolt. Surfaces carry a faint violet tint so white cards
// separate from the background without heavy shadows.
// ============================================================

// --- Brand ---
val MorphPrimary = Color(0xFF7952E8)          // CTA, selected item, FAB, active navigation
val MorphPrimaryDark = Color(0xFF633BD0)      // On primary container (light)
val MorphPrimaryLight = Color(0xFFA78BFA)     // Primary in dark theme
val MorphPrimaryContainer = Color(0xFFEEE8FF)
val MorphPrimaryContainerDark = Color(0xFF35275A)
val MorphSecondary = Color(0xFFB58AFF)
val MorphAccentPink = Color(0xFFEC83D3)       // Secondary accent, used sparingly

// --- Instant bolt: always yellow, in every theme (spec 10.4) ---
val MorphInstantBolt = Color(0xFFF5B51B)

// --- Semantic ---
val MorphSuccess = Color(0xFF249B69)
val MorphWarning = Color(0xFFD99A27)
val MorphError = Color(0xFFD94B55)

// --- Light surfaces (tonal ladder, lowest = cards) ---
val MorphLightBackground = Color(0xFFF6F4FB)
val MorphLightSurface = Color(0xFFFFFFFF)
val MorphLightSurfaceContainerLow = Color(0xFFFBFAFE)
val MorphLightSurfaceContainer = Color(0xFFF2EFF8)
val MorphLightSurfaceContainerHigh = Color(0xFFEBE7F4)
val MorphLightSurfaceContainerHighest = Color(0xFFE4DFEF)
val MorphLightSurfaceVariant = Color(0xFFF1EEF7)
val MorphLightTextPrimary = Color(0xFF20202A)
// Spec suggests #858493, which is only ~3.7:1 on white; this keeps the hue at ~5.3:1 (WCAG AA)
val MorphLightTextSecondary = Color(0xFF6A6779)
val MorphLightOutline = Color(0xFFD6D1E2)        // Outlined controls
val MorphLightOutlineVariant = Color(0xFFE8E5EF) // Hairlines and dividers (spec Outline)

// --- Dark surfaces (stepped, never a flat inversion) ---
val MorphDarkBackground = Color(0xFF111116)
val MorphDarkSurface = Color(0xFF1B1A22)
val MorphDarkSurfaceContainerLowest = Color(0xFF0D0C11)
val MorphDarkSurfaceContainerLow = Color(0xFF17161D)
val MorphDarkSurfaceContainer = Color(0xFF211F29)
val MorphDarkSurfaceContainerHigh = Color(0xFF2A2833)
val MorphDarkSurfaceContainerHighest = Color(0xFF34313E)
val MorphDarkSurfaceVariant = Color(0xFF25232E)
val MorphDarkTextPrimary = Color(0xFFF4F1FA)
val MorphDarkTextSecondary = Color(0xFFAAA5B5)
val MorphDarkOutline = Color(0xFF4B4757)
val MorphDarkOutlineVariant = Color(0xFF34313F)

// --- Error containers ---
val MorphErrorContainerLight = Color(0xFFFDE8EA)
val MorphErrorContainerDark = Color(0xFF3D1518)

// --- Instant / success containers ---
val MorphInstantContainerLight = Color(0xFFFFF4DA)
val MorphOnInstantContainerLight = Color(0xFF6E4A00)
val MorphInstantContainerDark = Color(0xFF3A2E12)
val MorphOnInstantContainerDark = Color(0xFFF7D98E)
val MorphSuccessContainerLight = Color(0xFFDDF4E9)
val MorphOnSuccessContainerLight = Color(0xFF145C3D)
val MorphSuccessContainerDark = Color(0xFF173A2B)
val MorphOnSuccessContainerDark = Color(0xFF9EE2C3)
