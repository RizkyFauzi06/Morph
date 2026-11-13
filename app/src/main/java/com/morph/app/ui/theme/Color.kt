package com.morph.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// MORPH DESIGN SYSTEM — COLOR TOKENS v1.0
// Sesuai MORPH_UI_UX_SPEC.md Section 2.2
// ============================================================

// --- Primary & Brand ---
val MorphPrimary        = Color(0xFF7952E8)   // CTA, selected item, FAB, active navigation
val MorphPrimaryDark    = Color(0xFF633BD0)   // Pressed state / emphasis
val MorphPrimaryLight   = Color(0xFFA78BFA)   // Dark theme primary
val MorphPrimaryContainer = Color(0xFFEEE8FF) // Selected chip, soft highlight (light)
val MorphPrimaryContainerDark = Color(0xFF35275A) // Selected chip (dark)

// --- Secondary & Accent ---
val MorphSecondary      = Color(0xFFB58AFF)   // Aksen ungu muda
val MorphAccentPink     = Color(0xFFEC83D3)   // Aksen visual sekunder

// --- Instant Bolt ---
val MorphInstantBolt    = Color(0xFFF5B51B)   // Ikon petir Instant — SELALU KUNING

// --- Semantic ---
val MorphSuccess        = Color(0xFF249B69)   // Berhasil
val MorphWarning        = Color(0xFFD99A27)   // Peringatan
val MorphError          = Color(0xFFD94B55)   // Error / cancel

// --- Light Theme Surfaces ---
val MorphLightBackground      = Color(0xFFFAFAFC)
val MorphLightSurface         = Color(0xFFFFFFFF)
val MorphLightSurfaceVariant  = Color(0xFFF3F1F7)
val MorphLightTextPrimary     = Color(0xFF20202A)
val MorphLightTextSecondary   = Color(0xFF858493)
val MorphLightOutline         = Color(0xFFE8E5EF)

// --- Dark Theme Surfaces ---
val MorphDarkBackground       = Color(0xFF111116)
val MorphDarkSurface          = Color(0xFF1B1A22)
val MorphDarkSurfaceVariant   = Color(0xFF25232E)
val MorphDarkTextPrimary      = Color(0xFFF4F1FA)
val MorphDarkTextSecondary    = Color(0xFFAAA5B5)
val MorphDarkOutline          = Color(0xFF393544)

// --- Error containers ---
val MorphErrorContainerLight  = Color(0xFFFDE8EA)
val MorphErrorContainerDark   = Color(0xFF3D1518)

// --- Legacy aliases (untuk komponen lama yang masih referensikan nama ini) ---
val MorphIndigo     = MorphPrimary
val MorphPink       = MorphAccentPink
val MorphViolet     = MorphSecondary
val MorphIndigoLight = MorphPrimaryLight
val MorphIndigoDark  = MorphPrimaryDark
val MorphVioletLight = MorphSecondary
val MorphPinkLight   = Color(0xFFF4ACDD)
val MorphLightBorder = MorphLightOutline
val MorphDarkBorder  = MorphDarkOutline
val MorphLightOnSurface = MorphLightTextPrimary
val MorphLightOnSurfaceVariant = MorphLightTextSecondary
val MorphDarkOnSurface = MorphDarkTextPrimary
val MorphDarkOnSurfaceVariant = MorphDarkTextSecondary
val MorphLightBg = MorphLightBackground
val MorphDarkBg = MorphDarkBackground
val MorphDarkSurfaceCard = Color(0xFF1F1D28)
val MorphLightSurfaceCard = MorphLightSurface
