package com.morph.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morph.app.MorphService
import com.morph.app.ui.components.formatFileSize

// --- HELPER: Deteksi apakah pasangan input→output mendukung Lossless Stream Copy ---
fun isLosslessCompatible(inputExt: String, outputExt: String, mimeType: String): Boolean {
    val videoContainerRemap = setOf("mp4", "mkv", "mov", "m4v", "ts")
    val audioLosslessRemap = setOf("m4a")
    return when {
        // Video ke video container yang sama codec-nya (H.264/H.265 remux)
        mimeType.startsWith("video/") &&
            inputExt in videoContainerRemap &&
            outputExt in videoContainerRemap &&
            inputExt != outputExt -> true
        // Audio AAC remux m4a → m4a
        mimeType.startsWith("audio/") &&
            inputExt in audioLosslessRemap &&
            outputExt in audioLosslessRemap -> true
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomSheetConverter(
    fileUri: Uri,
    fileName: String,
    mimeType: String,
    defaultQuality: Int,
    onDismiss: () -> Unit,
    onConvertStarted: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var fileSizeStr by remember { mutableStateOf("Menghitung ukuran...") }
    LaunchedEffect(fileUri) {
        val cursor = context.contentResolver.query(fileUri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex != -1) {
                val sizeInBytes = cursor.getLong(sizeIndex)
                fileSizeStr = formatFileSize(sizeInBytes)
            }
            cursor.close()
        } else {
            fileSizeStr = "Ukuran berkas siap"
        }
    }

    val inputExt = fileName.substringAfterLast('.', "").lowercase()
    var selectedQuality by remember { mutableStateOf(defaultQuality) }
    val formatsToOptions: List<String> = remember(fileName, mimeType) { getTargetFormats(fileName, mimeType) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
     ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pilih Format Tujuan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$fileName • $fileSizeStr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            // --- PEMILIH KUALITAS OUTPUT (PBI 11 & STREAM COPY) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode Kualitas:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("Tinggi", 0),
                    Pair("Sedang", 1),
                    Pair("Rendah", 2),
                    Pair("⚡ Instan", 3)
                ).forEach { (title, idx) ->
                    val isSelected = selectedQuality == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedQuality = idx },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center 
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val qualityHint = when (selectedQuality) {
                0 -> "Kualitas penuh (Bitrate 100% dipertahankan)"
                1 -> "Kompresi seimbang (Ukuran berkurang ~50%)"
                2 -> "Kompresi agresif (Resolusi 720p / hemat memori)"
                3 -> "⚡ Lossless Stream Copy: Sangat cepat, tanpa re-encode"
                else -> ""
            }
            Text(
                text = qualityHint,
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedQuality == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- FORMAT OPTIONS GRID (Using for loop instead of .forEach) ---
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (format in formatsToOptions) {
                    val isPdfCompress = format.lowercase() == "pdf" &&
                            (fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf")

                    // Auto-detect lossless: jika format didukung stream copy
                    val isLossless = isLosslessCompatible(inputExt, format.lowercase(), mimeType)
                    val buttonLabel = when {
                        isPdfCompress -> "KOMPRES PDF"
                        isLossless -> "⚡ ${format.uppercase()}"
                        else -> format.uppercase()
                    }

                    // Tentukan quality yang akan dikirim: lossless otomatis jika kompatibel
                    val effectiveQuality = if (isLossless) 3 else if (selectedQuality == 3) 0 else selectedQuality

                    val btnColor = if (isLossless) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    val btnTextColor = if (isLossless) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary

                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                putExtra("INPUT_URI", fileUri)
                                putExtra("TARGET_FORMAT", format)
                                putExtra("QUALITY_PRESET", effectiveQuality)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            onConvertStarted()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnColor,
                            contentColor = btnTextColor
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = buttonLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Info: jika user pilih "Instan" tapi format tidak lossless-compatible
            if (selectedQuality == 3) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ℹ️ Format tanpa ⚡ akan otomatis menggunakan kualitas encode tertinggi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

fun getTargetFormats(fileName: String, mimeType: String): List<String> {
    val originalExt = fileName.substringAfterLast('.', "").lowercase()
    val availableFormats = mutableListOf<String>()

    val isCompressible = mimeType.startsWith("video/") ||
            mimeType.startsWith("image/") ||
            mimeType.startsWith("audio/") ||
            mimeType == "application/pdf" ||
            originalExt == "pdf"

    when {
        mimeType.startsWith("video/") -> {
            availableFormats.addAll(listOf("mp4", "mkv", "avi", "mov", "gif", "mp3", "m4a"))
        }
        mimeType.startsWith("image/") -> {
            availableFormats.addAll(listOf("png", "jpg", "webp", "pdf"))
        }
        mimeType.startsWith("audio/") -> {
            availableFormats.addAll(listOf("mp3", "m4a", "wav"))
        }
        mimeType == "application/pdf" || originalExt == "pdf" -> {
            availableFormats.addAll(listOf("pdf", "jpg", "png"))
        }
        // SVG support
        originalExt == "svg" || mimeType == "image/svg+xml" -> {
            availableFormats.addAll(listOf("png", "jpg"))
        }
        // EPUB support
        originalExt == "epub" || mimeType == "application/epub+zip" -> {
            availableFormats.addAll(listOf("txt"))
        }
        else -> {
            availableFormats.addAll(listOf("mp4", "png", "mp3", "pdf"))
        }
    }

    if (!isCompressible) {
        availableFormats.remove(originalExt)
    } else {
        if (!availableFormats.contains(originalExt) && originalExt.isNotEmpty()) {
            availableFormats.add(0, originalExt)
        }
    }

    return availableFormats.distinct()
}
