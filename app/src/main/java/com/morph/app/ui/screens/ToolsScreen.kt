package com.morph.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morph.app.ui.theme.MorphIndigo
import com.morph.app.ui.theme.MorphPink
import com.morph.app.ui.theme.MorphViolet

@Composable
fun ToolsScreen(
    onPickFileForTool: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Pusat Alat & Utilitas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Modul konversi teknis & fitur ekspansi Morph",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // TOOL 1: Lossless Stream Copy (PBI 11)
        item {
            ToolFeatureCard(
                title = "Salin Stream (Lossless Copy)",
                subtitle = "Ubah kontainer video (MKV ke MP4, AVI, MOV) secara instan tanpa proses encode ulang. Bitrate dan kualitas 100% terjaga.",
                icon = Icons.Default.Bolt,
                iconTint = MorphIndigo,
                badgeText = "Sprint 3 Aktif",
                badgeColor = Color(0xFF10B981),
                onClick = onPickFileForTool
            )
        }

        // TOOL 2: PDF Toolkit (PBI 2.5 & 13)
        item {
            ToolFeatureCard(
                title = "PDF Smart Compressor & Renderer",
                subtitle = "Pangkas ukuran berkas PDF dokumen berbobot besar dan ekstrak setiap halaman menjadi gambar PNG/JPG berkualitas tinggi.",
                icon = Icons.Default.PictureAsPdf,
                iconTint = MorphPink,
                badgeText = "Sprint 3 Aktif",
                badgeColor = Color(0xFF10B981),
                onClick = onPickFileForTool
            )
        }

        // TOOL 3: Audio Extractor
        item {
            ToolFeatureCard(
                title = "Ekstraktor Audio Cepat",
                subtitle = "Ambil jalur audio dari klip video dan simpan sebagai file MP3, M4A, atau WAV murni.",
                icon = Icons.Default.Audiotrack,
                iconTint = MorphViolet,
                badgeText = "Sprint 3 Aktif",
                badgeColor = Color(0xFF10B981),
                onClick = onPickFileForTool
            )
        }

        // TOOL 4: SVG Converter (PBI Sprint 3)
        item {
            ToolFeatureCard(
                title = "SVG ke PNG/JPG",
                subtitle = "Render file vektor SVG menjadi gambar bitmap PNG atau JPG untuk dibagikan atau digunakan di berbagai aplikasi.",
                icon = Icons.Default.AutoAwesome,
                iconTint = Color(0xFF8B5CF6),
                badgeText = "Sprint 3 Aktif",
                badgeColor = Color(0xFF10B981),
                onClick = onPickFileForTool
            )
        }

        // TOOL 5: EPUB Converter (PBI Sprint 3)
        item {
            ToolFeatureCard(
                title = "EPUB ke TXT",
                subtitle = "Ekstrak seluruh isi teks dari buku digital EPUB menjadi file teks biasa yang bisa dibaca di aplikasi apapun.",
                icon = Icons.Default.Book,
                iconTint = Color(0xFFF97316),
                badgeText = "Sprint 3 Aktif",
                badgeColor = Color(0xFF10B981),
                onClick = onPickFileForTool
            )
        }

        // TOOL 6: Batch Queue Converter (PBI 8.3 Teaser)
        item {
            ToolFeatureCard(
                title = "Antrean Konversi Massal (Batch)",
                subtitle = "Pilih puluhan file sekaligus dan biarkan Morph mengonversinya secara teratur di latar belakang dengan antrean otomatis.",
                icon = Icons.Default.Layers,
                iconTint = Color(0xFFF59E0B),
                badgeText = "Segera di Sprint 4",
                badgeColor = Color(0xFFF59E0B),
                onClick = {}
            )
        }

        // TOOL 7: Web-to-Morph (PBI 9 Teaser)
        item {
            ToolFeatureCard(
                title = "Web-to-Morph (URL Catcher)",
                subtitle = "Tempel tautan video/audio web dan unduh langsung ke format pilihan Anda di penyimpanan lokal.",
                icon = Icons.Default.Language,
                iconTint = Color(0xFF06B6D4),
                badgeText = "Segera di Sprint 4",
                badgeColor = Color(0xFF06B6D4),
                onClick = {}
            )
        }
    }
}

@Composable
private fun ToolFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
