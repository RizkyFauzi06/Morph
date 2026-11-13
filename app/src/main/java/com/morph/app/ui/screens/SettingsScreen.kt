package com.morph.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morph.app.MorphPrefs
import com.morph.app.ui.components.MorphLogo
import com.morph.app.ui.components.MorphScreenHeader
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Surface
import com.morph.app.BuildConfig

@Composable
fun SettingsScreen(
    currentTheme: Int,
    onThemeChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { MorphPrefs(context) }

    var keepName by remember { mutableStateOf(prefs.keepOriginalName) }
    var closeShare by remember { mutableStateOf(prefs.closeAfterShare) }
    var qualityPreset by remember { mutableStateOf(prefs.qualityPreset) }

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
                // Segmented control: Terang, Gelap, Sistem — sesuai spec
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

        // --- 2. KUALITAS KONVERSI DEFAULT ---
        item {
            SettingsSectionCard(title = "Kualitas Konversi Default", icon = Icons.Default.Tune) {
                Text(
                    text = "Preset bawaan yang digunakan saat membuka panel konversi. Dapat diubah per sesi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Urutan sesuai spec: Rendah, Sedang, Tinggi, Instan
               Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp) // Kecilkan sedikit jaraknya
                ) {
                    listOf(
                        Pair("Rendah", 2),
                        Pair("Sedang", 1),
                        Pair("Tinggi", 0),
                        Pair("Instan", 3)
                    ).forEach { (title, idx) ->
                        val isSelected = qualityPreset == idx
                        Surface(
                            modifier = Modifier.weight(1f), // Tetap pakai weight agar terbagi rata 4
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                qualityPreset = idx
                                prefs.qualityPreset = idx
                            }
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(vertical = 8.dp), // Hanya padding atas-bawah
                                textAlign = TextAlign.Center, // Teks otomatis ke tengah
                                style = MaterialTheme.typography.labelMedium, // Pakai ukuran yang sedikit lebih kecil
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

        // --- 3. PERILAKU APLIKASI ---
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
                }
            }
        }

        // --- 4. TENTANG MORPH ---
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
            }
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
