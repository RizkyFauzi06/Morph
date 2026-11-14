package com.morph.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morph.app.BuildConfig
import com.morph.app.MorphPrefs
import com.morph.app.ui.components.MorphScreenHeader

@Composable
fun SettingsScreen(
    currentTheme: Int,
    onThemeChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentLanguage: String? = null,
    onLanguageChange: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { MorphPrefs(context) }

    var selectedLanguage by remember(currentLanguage) {
        mutableStateOf(currentLanguage ?: prefs.appLanguage)
    }
    var keepName by remember { mutableStateOf(prefs.keepOriginalName) }
    var closeShare by remember { mutableStateOf(prefs.closeAfterShare) }
    var qualityPreset by remember { mutableStateOf(prefs.qualityPreset) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isBatteryOk by remember {
        mutableStateOf(com.morph.app.checkBatteryOptimizationDisabled(context))
    }
    var showBatteryDialogInSettings by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isBatteryOk = com.morph.app.checkBatteryOptimizationDisabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header — tanpa back button (bottom nav sudah ada)
        item {
            MorphScreenHeader(
                title = "Pengaturan",
                subtitle = "Konfigurasi tampilan dan perilaku Morph."
            )
        }

        // --- 1. TEMA APLIKASI ---
        item {
            SettingsSectionCard(title = "Tema Aplikasi", icon = Icons.Default.Palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Terang", 1, "☀️"),
                        Triple("Gelap", 2, "🌙"),
                        Triple("Sistem", 0, "📱")
                    ).forEach { (title, idx, emoji) ->
                        val isSelected = currentTheme == idx
                        FilterChip(
                            selected = isSelected,
                            onClick = { onThemeChange(idx) },
                            label = {
                                Text(
                                    text = "$emoji $title",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // --- 2. BAHASA / LANGUAGE ---
        item {
            SettingsSectionCard(title = "Bahasa / Language", icon = Icons.Default.Language) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Bahasa Indonesia", "id", "🇮🇩"),
                        Triple("English", "en", "🇬🇧")
                    ).forEach { (label, code, flag) ->
                        val isSelected = selectedLanguage == code
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLanguage = code
                                prefs.appLanguage = code
                                onLanguageChange?.invoke(code)
                                val msg = if (code == "en") {
                                    "Language set to English"
                                } else {
                                    "Bahasa diatur ke Bahasa Indonesia"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            label = {
                                Text(
                                    text = "$flag $label",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // --- 3. KUALITAS KONVERSI DEFAULT ---
        item {
            SettingsSectionCard(title = "Kualitas Konversi Default", icon = Icons.Default.Tune) {
                Text(
                    text = "Preset bawaan yang digunakan saat membuka panel konversi. Dapat diubah per sesi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair("Rendah", 2),
                        Pair("Sedang", 1),
                        Pair("Tinggi", 0),
                        Pair("Instan", 3)
                    ).forEach { (title, idx) ->
                        val isSelected = qualityPreset == idx
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                qualityPreset = idx
                                prefs.qualityPreset = idx
                            }
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val desc = when (qualityPreset) {
                    0 -> "Tinggi: bitrate 100% dipertahankan tanpa kompresi tambahan."
                    1 -> "Sedang: kompresi seimbang, ukuran berkurang ~50%."
                    2 -> "Rendah: kompresi agresif untuk hemat ruang."
                    3 -> "Instan: stream copy cepat tanpa re-encode (kualitas asli, hanya untuk format yang kompatibel)."
                    else -> ""
                }
                if (desc.isNotEmpty()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- 4. PERILAKU APLIKASI ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                
            ) {
                Column {
                    // Pertahankan Nama Asli
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Pertahankan Nama Asli",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pertahankan nama dasar file asli. Morph tidak menambahkan imbuhan nama baru, kecuali diperlukan untuk menghindari nama yang bertabrakan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepName,
                            onCheckedChange = {
                                keepName = it
                                prefs.keepOriginalName = it
                            }
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // Tutup Otomatis Share
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Tutup Otomatis (Share)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tutup bottom sheet setelah format konversi dipilih tanpa mengarahkan pengguna ke aplikasi Morph.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = closeShare,
                            onCheckedChange = {
                                closeShare = it
                                prefs.closeAfterShare = it
                            }
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    val isBatteryOk = remember { com.morph.app.checkBatteryOptimizationDisabled(context) }
                    var showBatteryDialogInSettings by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBatteryDialogInSettings = true }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Optimasi Baterai Latar Belakang",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBatteryOk) "Status: Tidak dibatasi (Optimal)" else "Status: Dibatasi · Ketuk untuk melihat cara mengatur",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isBatteryOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // --- 5. TENTANG MORPH & LISENSI OPEN SOURCE ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tentang Morph",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Versi ${BuildConfig.VERSION_NAME} · Offline File Converter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Dirancang oleh Rizki Fauzi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLicensesDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "Lisensi Open Source",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "GPL-3.0, Apache 2.0 & pustaka pihak ketiga",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(onDismiss = { showLicensesDialog = false })
    }
    
    if (showBatteryDialogInSettings) {
        BatteryOptimizationGuideDialog(
            onDismiss = { showBatteryDialogInSettings = false },
            onOpenSettings = {
                showBatteryDialogInSettings = false
                com.morph.app.openAppBatterySettings(context)
            }
        )
    }
}

// ============================================================
// OPEN SOURCE LICENSES DIALOG
// ============================================================

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Lisensi Open Source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Morph didistribusikan di bawah GNU General Public License v3.0 (GPL-3.0) dan menggunakan pustaka open-source berikut:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LicenseItemCard(
                    name = "FFmpegKit Full (v8.1.7)",
                    license = "GNU General Public License v3.0 (GPL-3.0) / LGPL-3.0",
                    copyright = "Copyright (c) Arlen Ceylan & FFmpeg Developers"
                )

                LicenseItemCard(
                    name = "AndroidSVG (v1.4)",
                    license = "Apache License, Version 2.0",
                    copyright = "Copyright 2013–2019 Paul LeBeau, Cave Rock Software Ltd."
                )

                LicenseItemCard(
                    name = "Kotlin, Coroutines & Android Jetpack (Compose)",
                    license = "Apache License, Version 2.0",
                    copyright = "Copyright JetBrains s.r.o. & The Android Open Source Project"
                )

                LicenseItemCard(
                    name = "Android Native SDK (PdfRenderer, PdfDocument, java.util.zip)",
                    license = "Apache License, Version 2.0",
                    copyright = "Copyright The Android Open Source Project"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Tutup",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Composable
private fun LicenseItemCard(
    name: String,
    license: String,
    copyright: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = license,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================
// SETTINGS SECTION CARD HELPER
// ============================================================

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}