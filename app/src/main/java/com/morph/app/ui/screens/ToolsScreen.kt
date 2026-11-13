
package com.morph.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morph.app.ui.components.MorphScreenHeader

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
        contentPadding = PaddingValues(
            top = 20.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header — sesuai spec: "Tools" + deskripsi singkat
        item {
            MorphScreenHeader(
                title = "Tools",
                subtitle = "Kenali fitur dan kemampuan Morph."
            )
        }

        // Conversion Tools Section
        item {
            SectionHeading(
                title = "Alat Konversi",
                subtitle = "Konversi file bawaan Morph."
            )
        }

        item {
            ToolFeatureCard(
                title = "Video Conversion",
                description = "Mengubah container video dengan proses cepat tanpa encoding ulang. Ukuran hasil dapat sedikit berbeda.",
                supportedFormats = "MKV · MP4 · MOV  →  MP4 · MOV · MKV",
                icon = Icons.Default.VideoFile
            )
        }

        item {
            ToolFeatureCard(
                title = "PDF Toolkit",
                description = "Kompres dokumen PDF atau ubah setiap halaman menjadi gambar yang dapat dibuka dan dibagikan.",
                supportedFormats = "PDF · JPG · PNG · WEBP  →  PDF · PNG · JPG",
                icon = Icons.Default.PictureAsPdf
            )
        }

        item {
            ToolFeatureCard(
                title = "Audio Extraction",
                description = "Ekstrak jalur audio dari video dan simpan dalam berbagai format audio.",
                supportedFormats = "MP4 · MKV · MOV · WEBM  →  MP3 · M4A · WAV · FLAC · OGG · OPUS",
                icon = Icons.Default.Audiotrack
            )
        }

        item {
            ToolFeatureCard(
                title = "SVG Conversion",
                description = "Ubah grafik vektor SVG menjadi gambar raster yang dapat dibuka di galeri atau dibagikan.",
                supportedFormats = "SVG  →  PNG · JPG · WEBP",
                icon = Icons.Default.AutoAwesome
            )
        }

        item {
            ToolFeatureCard(
                title = "EPUB Text Extraction",
                description = "Ekstrak isi dan bab buku digital menjadi satu file teks yang lebih ringan dan mudah dibaca.",
                supportedFormats = "EPUB  →  TXT",
                icon = Icons.Default.Book
            )
        }

        item {
            ToolFeatureCard(
                title = "Image Conversion",
                description = "Ubah format gambar dengan penanganan transparansi dan pilihan output yang beragam.",
                supportedFormats = "PNG · JPG · WEBP · BMP  →  PNG · JPG · WEBP · BMP · PDF",
                icon = Icons.Default.Image
            )
        }

        // Share & Usage Section
        item {
            SectionHeading(
                title = "Cara Lain Menggunakan Morph",
                subtitle = "Gunakan Morph langsung dari aplikasi lain."
            )
        }

        item {
            InfoFeatureCard(
                title = "Share to Morph",
                description = "Kirim file langsung dari aplikasi lain ke Morph melalui menu Share Android.",
                detail = "Pilih satu atau beberapa file dari Gallery, File Manager, atau aplikasi lain yang mendukung berbagi file. Morph menerima file tersebut dan menyiapkan pilihan konversi tanpa perlu mencarinya kembali secara manual.",
                icon = Icons.Default.Share
            )
        }

        item {
            InfoFeatureCard(
                title = "Batch Conversion",
                description = "Pilih beberapa file sekaligus dan proses semuanya dalam satu sesi konversi.",
                detail = "Morph dapat mengelompokkan file berdasarkan format sumber sehingga setiap kelompok dapat memiliki format tujuan dan pengaturan yang sesuai.",
                icon = Icons.Default.DynamicFeed
            )
        }

        item {
            InfoFeatureCard(
                title = "Offline Conversion",
                description = "Konversi file secara lokal di perangkat.",
                detail = "Untuk format yang didukung, proses konversi dilakukan tanpa perlu mengunggah file ke layanan cloud.",
                icon = Icons.Default.WifiOff
            )
        }

        item {
            InfoFeatureCard(
                title = "Format Support",
                description = "Lihat format sumber dan tujuan yang tersedia.",
                detail = "Video: MP4, MKV, MOV, WEBM, AVI\nAudio: MP3, WAV, FLAC, AAC, M4A, OGG, OPUS\nGambar: PNG, JPG, WEBP, BMP, SVG\nDokumen: PDF, TXT, EPUB",
                icon = Icons.Default.Extension
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolFeatureCard(
    title: String,
    description: String,
    supportedFormats: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FormatLabel(supportedFormats)
        }
    }
}

@Composable
private fun InfoFeatureCard(
    title: String,
    description: String,
    detail: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun FeatureIcon(
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FormatLabel(
    formats: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = formats,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 7.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


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
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Tools",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Kenali fitur yang tersedia di Morph.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Conversion Tools
        item {
            SectionHeading(
                title = "Conversion Tools",
                subtitle = "Alat konversi file bawaan Morph."
            )
        }

        item {
            ToolFeatureCard(
                title = "Video Conversion",
                description = "Mengubah container video dengan proses cepat tanpa encoding ulang. Ukuran hasil dapat sedikit berbeda.",
                supportedFormats = "MKV · MP4 · MOV  →  MP4 · MOV · MKV",
                icon = Icons.Default.VideoFile
            )
        }

        item {
            ToolFeatureCard(
                title = "PDF Toolkit",
                description = "Kompres dokumen PDF atau ubah setiap halaman menjadi gambar yang dapat dibuka dan dibagikan.",
                supportedFormats = "PDF · JPG · PNG · WEBP  →  PDF · PNG · JPG",
                icon = Icons.Default.PictureAsPdf
            )
        }

        item {
            ToolFeatureCard(
                title = "Audio Extraction",
                description = "Ekstrak jalur audio dari video dan simpan dalam berbagai format audio.",
                supportedFormats = "MP4 · MKV · MOV · WEBM  →  MP3 · M4A · WAV · FLAC · OGG · OPUS",
                icon = Icons.Default.Audiotrack
            )
        }

        item {
            ToolFeatureCard(
                title = "SVG Conversion",
                description = "Ubah grafik vektor SVG menjadi gambar raster yang dapat dibuka di galeri atau dibagikan.",
                supportedFormats = "SVG  →  PNG · JPG · WEBP",
                icon = Icons.Default.AutoAwesome
            )
        }

        item {
            ToolFeatureCard(
                title = "EPUB Text Extraction",
                description = "Ekstrak isi dan bab buku digital menjadi satu file teks yang lebih ringan dan mudah dibaca.",
                supportedFormats = "EPUB  →  TXT",
                icon = Icons.Default.Book
            )
        }

        item {
            ToolFeatureCard(
                title = "Image Conversion",
                description = "Ubah format gambar dengan penanganan transparansi dan pilihan output yang beragam.",
                supportedFormats = "PNG · JPG · WEBP · BMP  →  PNG · JPG · WEBP · BMP · PDF",
                icon = Icons.Default.Image
            )
        }

        // Sharing Features
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionHeading(
                    title = "More Ways to Use Morph",
                    subtitle = "Gunakan Morph langsung dari aplikasi lain."
                )
            }
        }
        
        item {
            InfoFeatureCard(
                title = "Share to Morph",
                description = "Convert file langsung dari aplikasi lain ke Morph melalui menu Share Android.",
                detail = "Pilih satu atau beberapa file dari Gallery, File Manager, atau aplikasi lain yang mendukung berbagi file. Morph akan menerima file tersebut dan menyiapkan proses konversi tanpa perlu mencarinya kembali secara manual.",
                icon = Icons.Default.Share
            )
        }

        item {
            InfoFeatureCard(
                title = "Web to Morph",
                description = "Temukan Gambar dari web lalu bagikan langsung ke Morph tanpa perlu membuka aplikasi secara terpisah.",
                detail = "Contohnya, PDF dari Google Drive atau gambar dari Google Images. Gunakan menu Bagikan dan pilih Morph untuk memulai konversi.",
                icon = Icons.Default.Language
            )
        }

        item {
            InfoFeatureCard(
                title = "Batch Processing",
                description = "Pilih beberapa file sekaligus dan proses semuanya dalam satu antrean konversi.",
                detail = "Morph akan mengatur file dan memprosesnya secara berurutan, sehingga kamu tidak perlu memulai konversi satu per satu.",
                icon = Icons.Default.DynamicFeed
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolFeatureCard(
    title: String,
    description: String,
    supportedFormats: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.30f
            )
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(icon)

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            FormatLabel(supportedFormats)
        }
    }
}

@Composable
private fun InfoFeatureCard(
    title: String,
    description: String,
    detail: String,
    icon: ImageVector,
    status: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.30f
            )
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(icon)

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (status != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 5.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun FeatureIcon(
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = 0.75f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FormatLabel(
    formats: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Text(
            text = formats,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 7.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}
