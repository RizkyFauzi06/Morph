package com.morph.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.morph.app.core.ConversionCapabilityManager
import com.morph.app.core.ConversionJobRequest
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.SelectedFileItem
import com.morph.app.ui.components.formatFileSize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomSheetConverter(
    selectedFiles: List<SelectedFileItem>,
    defaultQuality: Int,
    onDismiss: () -> Unit,
    onConvertStarted: () -> Unit
) {
    if (selectedFiles.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val isBatchMode = selectedFiles.size > 1
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var selectedQuality by remember { mutableStateOf(defaultQuality) }

    // Hitung ukuran total atau per file
    val totalSize = remember(selectedFiles) {
        selectedFiles.sumOf { it.sizeBytes }
    }

    // Kelompokkan berkas berdasarkan ekstensi sumber
    val groupedFiles = remember(selectedFiles) {
        selectedFiles.groupBy { file ->
            file.fileName.substringAfterLast('.', "lain").trim().lowercase()
        }
    }

    // State pilihan format target per kelompok (key: format sumber, value: format target yang dipilih)
    val targetFormatPerGroup = remember(groupedFiles) {
        mutableStateMapOf<String, String>().apply {
            groupedFiles.forEach { (srcExt, files) ->
                val sampleFile = files.first()
                val availableTargets = ConversionCapabilityManager.getTargetFormats(sampleFile.fileName, sampleFile.mimeType)
                val defaultTarget = availableTargets.firstOrNull { it != srcExt } ?: availableTargets.firstOrNull() ?: ""
                put(srcExt, defaultTarget)
            }
        }
    }

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
                .padding(top = 4.dp, bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER ---
            Text(
                text = if (isBatchMode) "Konfigurasi Konversi Batch" else "Pilih Format Tujuan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitleText = if (isBatchMode) {
                val sizeText = if (totalSize > 0) " • ${formatFileSize(totalSize)}" else ""
                "${selectedFiles.size} berkas terpilih dalam ${groupedFiles.size} kelompok format$sizeText"
            } else {
                val single = selectedFiles.first()
                val sizeText = if (single.sizeBytes > 0) " • ${formatFileSize(single.sizeBytes)}" else ""
                "${single.fileName}$sizeText"
            }

            Text(
                text = subtitleText,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                3 -> "⚡ Lossless Stream Copy: Sangat cepat jika format kompatibel"
                else -> ""
            }
            Text(
                text = qualityHint,
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedQuality == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- FORMAT SELECTION ---
            if (!isBatchMode) {
                // Single File Mode: Grid pilihan format langsung
                val singleFile = selectedFiles.first()
                val inputExt = singleFile.fileName.substringAfterLast('.', "").trim().lowercase()
                val formatsToOptions = remember(singleFile.fileName, singleFile.mimeType) {
                    ConversionCapabilityManager.getTargetFormats(singleFile.fileName, singleFile.mimeType)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (format in formatsToOptions) {
                        val isPdfCompress = format.lowercase() == "pdf" &&
                                (singleFile.fileName.endsWith(".pdf", ignoreCase = true) || singleFile.mimeType == "application/pdf")

                        val isLossless = ConversionCapabilityManager.isLosslessCompatible(inputExt, format.lowercase(), singleFile.mimeType)
                        val effectiveQuality = if (selectedQuality == 3) {
                            if (isLossless) 3 else 0
                        } else {
                            selectedQuality
                        }

                        val isCurrentStreamCopy = effectiveQuality == 3
                        val buttonLabel = when {
                            isPdfCompress -> "KOMPRES PDF"
                            isCurrentStreamCopy -> "⚡ ${format.uppercase()}"
                            else -> format.uppercase()
                        }

                        val btnColor = if (isCurrentStreamCopy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        val btnTextColor = if (isCurrentStreamCopy) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary

                        Button(
                            onClick = {
                                val serviceIntent = Intent(context, MorphService::class.java).apply {
                                    putExtra("INPUT_URI", singleFile.uri)
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
            } else {
                // Batch Mode: Tampilkan pengelompokan format sumber dengan pilihan target per kelompok
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    groupedFiles.forEach { (srcExt, filesInGroup) ->
                        val sampleFile = filesInGroup.first()
                        val availableTargets = remember(srcExt) {
                            ConversionCapabilityManager.getTargetFormats(sampleFile.fileName, sampleFile.mimeType)
                        }
                        val currentSelectedTarget = targetFormatPerGroup[srcExt] ?: ""

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = srcExt.uppercase(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${filesInGroup.size} berkas",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = "Target: ${currentSelectedTarget.uppercase()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Format target options chips
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    availableTargets.forEach { targetOption ->
                                        val isSelected = currentSelectedTarget.equals(targetOption, ignoreCase = true)
                                        val isLossless = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetOption)
                                        val labelText = if (isLossless && selectedQuality == 3) "⚡ ${targetOption.uppercase()}" else targetOption.uppercase()

                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { targetFormatPerGroup[srcExt] = targetOption },
                                            label = {
                                                Text(
                                                    text = labelText,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tombol Eksekusi Batch
                    val allGroupsConfigured = groupedFiles.keys.all { key ->
                        !targetFormatPerGroup[key].isNullOrBlank()
                    }

                    Button(
                        onClick = {
                            val requests = selectedFiles.map { file ->
                                val srcExt = file.fileName.substringAfterLast('.', "").trim().lowercase()
                                val targetFormat = targetFormatPerGroup[srcExt] ?: "mp4"

                                val isLossless = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetFormat, file.mimeType)
                                val effectiveQuality = if (selectedQuality == 3) {
                                    if (isLossless) 3 else 0
                                } else {
                                    selectedQuality
                                }

                                ConversionJobRequest(
                                    inputUri = file.uri,
                                    originalFileName = file.fileName,
                                    mimeType = file.mimeType,
                                    targetFormat = targetFormat,
                                    qualityPreset = effectiveQuality
                                )
                            }

                            // Tambahkan seluruh tugas ke antrean
                            ConversionQueueManager.addJobs(requests)

                            // Jalankan Foreground Service untuk memproses antrean
                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                action = MorphService.ACTION_START_QUEUE
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }

                            onConvertStarted()
                        },
                        enabled = allGroupsConfigured,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Mulai Konversi (${selectedFiles.size} Berkas)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Info hint untuk opsi Instan
            if (selectedQuality == 3) {
                Spacer(modifier = Modifier.height(10.dp))
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

// Overload untuk kompatibilitas file tunggal
@Composable
fun BottomSheetConverter(
    fileUri: Uri,
    fileName: String,
    mimeType: String,
    defaultQuality: Int,
    onDismiss: () -> Unit,
    onConvertStarted: () -> Unit
) {
    BottomSheetConverter(
        selectedFiles = listOf(SelectedFileItem(fileUri, fileName, mimeType)),
        defaultQuality = defaultQuality,
        onDismiss = onDismiss,
        onConvertStarted = onConvertStarted
    )
}
