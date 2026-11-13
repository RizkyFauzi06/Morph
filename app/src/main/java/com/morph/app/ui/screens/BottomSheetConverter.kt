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
import com.morph.app.ui.theme.MorphInstantBolt

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

    val totalSize = remember(selectedFiles) { selectedFiles.sumOf { it.sizeBytes } }

    // Kelompokkan berkas berdasarkan format sumber
    val groupedFiles = remember(selectedFiles) {
        selectedFiles.groupBy { file ->
            file.fileName.substringAfterLast('.', "lain").trim().lowercase()
        }
    }

    // Format target per kelompok (key: format sumber, value: target format)
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

    // Kualitas konversi per kelompok (key: format sumber, value: preset kualitas)
    val qualityPerGroup = remember(groupedFiles) {
        mutableStateMapOf<String, Int>().apply {
            groupedFiles.keys.forEach { srcExt ->
                put(srcExt, defaultQuality)
            }
        }
    }

    // Pilihan Gabung/Pisahkan PDF per grup gambar
    val pdfMergeOptionPerGroup = remember(groupedFiles) {
        mutableStateMapOf<String, Boolean>().apply {
            groupedFiles.keys.forEach { srcExt ->
                put(srcExt, true) // default: gabung satu PDF
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
            // Header
            Text(
                text = "Konversi File",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitleText = if (isBatchMode) {
                val sizeText = if (totalSize > 0) " • ${formatFileSize(totalSize)}" else ""
                "${selectedFiles.size} file terpilih dalam ${groupedFiles.size} kelompok format$sizeText"
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

            // Konfigurasi per kelompok format
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedFiles.forEach { (srcExt, filesInGroup) ->
                    val sampleFile = filesInGroup.first()
                    val availableTargets = remember(srcExt) {
                        ConversionCapabilityManager.getTargetFormats(sampleFile.fileName, sampleFile.mimeType)
                    }
                    val currentTarget = targetFormatPerGroup[srcExt] ?: ""
                    val isCurrentTargetLossless = remember(srcExt, currentTarget) {
                        ConversionCapabilityManager.isLosslessCompatible(srcExt, currentTarget, sampleFile.mimeType)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header grup sumber
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
                                        text = "${filesInGroup.size} file",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (currentTarget.isNotEmpty()) {
                                    Text(
                                        text = "Target: ${currentTarget.uppercase()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Format tujuan — Horizontal Chips
                            Text(
                                text = "Format Tujuan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableTargets.forEach { targetOption ->
                                    val isSelected = currentTarget.equals(targetOption, ignoreCase = true)
                                    val supportsInstant = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetOption, sampleFile.mimeType)

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { targetFormatPerGroup[srcExt] = targetOption },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = targetOption.uppercase(),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 12.sp
                                                )
                                                if (supportsInstant) {
                                                    Text(
                                                        text = "⚡",
                                                        color = MorphInstantBolt,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                    )
                                }
                            }

                            // Opsi khusus PNG/Gambar ke PDF: Gabung atau Pisah
                            val isImageToPdf = srcExt in listOf("png", "jpg", "jpeg", "webp") && currentTarget.equals("pdf", ignoreCase = true)
                            if (isImageToPdf && filesInGroup.size > 1) {
                                val isMerge = pdfMergeOptionPerGroup[srcExt] ?: true
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Opsi PDF",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = isMerge,
                                            onClick = { pdfMergeOptionPerGroup[srcExt] = true },
                                            label = {
                                                Text(
                                                    text = "Gabung 1 PDF",
                                                    fontWeight = if (isMerge) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                        FilterChip(
                                            selected = !isMerge,
                                            onClick = { pdfMergeOptionPerGroup[srcExt] = false },
                                            label = {
                                                Text(
                                                    text = "Pisah PDF",
                                                    fontWeight = if (!isMerge) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }

                            // Kualitas Konversi per grup
                            val currentQuality = qualityPerGroup[srcExt] ?: defaultQuality
                            val qualityOptions = if (isCurrentTargetLossless) {
                                listOf(Pair("Rendah", 2), Pair("Sedang", 1), Pair("Tinggi", 0), Pair("Instan", 3))
                            } else {
                                listOf(Pair("Rendah", 2), Pair("Sedang", 1), Pair("Tinggi", 0))
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Kualitas Konversi",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    qualityOptions.forEach { (label, qIdx) ->
                                        val isSelected = currentQuality == qIdx
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { qualityPerGroup[srcExt] = qIdx },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (qIdx == 3) {
                                                    Text(text = "⚡ ", color = MorphInstantBolt, fontSize = 12.sp)
                                                }
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!isCurrentTargetLossless && currentQuality == 3) {
                                    Text(
                                        text = "Instant tidak tersedia untuk format ini. Morph menggunakan kualitas tertinggi.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Validasi & CTA
            val allGroupsConfigured = groupedFiles.keys.all { key ->
                !targetFormatPerGroup[key].isNullOrBlank()
            }

            Button(
                onClick = {
                    val requests = selectedFiles.map { file ->
                        val srcExt = file.fileName.substringAfterLast('.', "").trim().lowercase()
                        val targetFormat = targetFormatPerGroup[srcExt] ?: "mp4"
                        val groupQuality = qualityPerGroup[srcExt] ?: defaultQuality
                        val isLossless = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetFormat, file.mimeType)
                        val effectiveQuality = if (groupQuality == 3 && !isLossless) 0 else groupQuality

                        ConversionJobRequest(
                            inputUri = file.uri,
                            originalFileName = file.fileName,
                            mimeType = file.mimeType,
                            targetFormat = targetFormat,
                            qualityPreset = effectiveQuality
                        )
                    }

                    ConversionQueueManager.addJobs(requests)

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
                    text = if (isBatchMode) "Konversi (${selectedFiles.size} File)" else "Konversi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
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
