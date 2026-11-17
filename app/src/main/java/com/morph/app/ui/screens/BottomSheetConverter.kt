package com.morph.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.morph.app.MorphService
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionCapabilityManager
import com.morph.app.core.ConversionJobRequest
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.SelectedFileItem
import com.morph.app.ui.components.formatFileSize
import com.morph.app.ui.theme.MorphInstantBolt
import com.morph.app.core.FileCategoryManager
import com.morph.app.ui.theme.MorphDarkBackground
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Book
import com.morph.app.core.FileCategory

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
    val strings = AppStrings.current
    val isDarkTheme = MaterialTheme.colorScheme.background == MorphDarkBackground

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

    // Cross-group PDF merge (e.g. WhatsApp mixes .jpg and .jpeg): default on, user can turn it off
    var crossMergePdf by remember(groupedFiles) { mutableStateOf(true) }
    val crossMergeGroups = ConversionCapabilityManager.pdfCrossMergeGroups(targetFormatPerGroup.toMap())
    val crossMergeActive = crossMergePdf && crossMergeGroups.isNotEmpty()

    // PBI 5.2: resolusi (sisi pendek, 0 = asli) & FPS (0 = asli) per grup video
    val resolutionPerGroup = remember(groupedFiles) { mutableStateMapOf<String, Int>() }
    val fpsPerGroup = remember(groupedFiles) { mutableStateMapOf<String, Int>() }

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
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val subtitleText = if (isBatchMode) {
                val sizeText = if (totalSize > 0) " • ${formatFileSize(totalSize)}" else ""
                strings.bottomSheetBatchSubtitle(selectedFiles.size, groupedFiles.size, sizeText)
            } else {
                val single = selectedFiles.first()
                val sizeText = if (single.sizeBytes > 0) " • ${formatFileSize(single.sizeBytes)}" else ""
                strings.bottomSheetSingleSubtitle(single.fileName, sizeText)
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⇄", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        strings.bottomSheetTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier.size(34.dp).clickable { onDismiss() },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", fontSize = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(
                strings.targetFormatHeader,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Konfigurasi per kelompok format
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    // Was "image or else Document", so video/audio groups showed as Dokumen
                    val sourceCategory = FileCategoryManager.getCategory(srcExt, sampleFile.mimeType)
                    val categoryLabel = sourceCategory.getLocalizedName(strings)
                    val categoryIcon = when (sourceCategory) {
                        FileCategory.VIDEO -> Icons.Default.VideoFile
                        FileCategory.AUDIO -> Icons.Default.Audiotrack
                        FileCategory.IMAGE -> Icons.Default.Image
                        FileCategory.PDF -> Icons.Default.PictureAsPdf
                        FileCategory.EBOOK -> Icons.Default.Book
                        else -> Icons.Default.Description
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                           val categoryColors = FileCategoryManager.getCategoryColors(
                                category = sourceCategory,
                                isDarkTheme = isDarkTheme
                            )
                            // Category header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    color = categoryColors.background
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = categoryIcon,
                                            contentDescription = null, // the label text sits right next to it
                                            modifier = Modifier.size(22.dp),
                                            tint = categoryColors.icon
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(categoryLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${srcExt.uppercase()} • ${filesInGroup.size} file${if (filesInGroup.size > 1) strings.filesCountSuffix else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        currentTarget.uppercase().ifBlank { strings.chooseFormat },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (availableTargets.isEmpty()) {
                                Text(
                                    strings.noTargetFormatAvailable,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            // Target format chips
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableTargets.forEach { targetOption ->
                                    val isSelected = currentTarget.equals(targetOption, ignoreCase = true)
                                    val supportsInstant = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetOption, sampleFile.mimeType)

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { targetFormatPerGroup[srcExt] = targetOption },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    targetOption.uppercase(),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 11.sp
                                                )
                                                if (supportsInstant) Text("⚡", color = MorphInstantBolt, fontSize = 11.sp)
                                            }
                                        },
                                        shape = RoundedCornerShape(50),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    )
                                }
                            }

                            // Opsi khusus PNG/Gambar ke PDF: Gabung atau Pisah
                            val isImageToPdf = srcExt in listOf("png", "jpg", "jpeg", "webp") && currentTarget.equals("pdf", ignoreCase = true)
                            if (isImageToPdf && filesInGroup.size > 1 && !(crossMergeActive && srcExt in crossMergeGroups)) {
                                val isMerge = pdfMergeOptionPerGroup[srcExt] ?: true
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(13.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(strings.mergeToPdfTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            Text(strings.mergeToPdfDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = isMerge,
                                            onCheckedChange = { pdfMergeOptionPerGroup[srcExt] = it }
                                        )
                                    }
                                }
                            }

                            // Kualitas konversi
                            val currentQuality = qualityPerGroup[srcExt] ?: defaultQuality
                            val qualityOptions = if (isCurrentTargetLossless) {
                                listOf(Pair(strings.qualityLow, 2), Pair(strings.qualityMedium, 1), Pair(strings.qualityHigh, 0), Pair(strings.qualityInstant, 3))
                            } else {
                                listOf(Pair(strings.qualityLow, 2), Pair(strings.qualityMedium, 1), Pair(strings.qualityHigh, 0))
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        strings.qualityTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text("ⓘ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    qualityOptions.forEach { (label, qIdx) ->
                                        val isSelected = currentQuality == qIdx
                                        Surface(
                                            modifier = Modifier.weight(1f).height(38.dp).clickable { qualityPerGroup[srcExt] = qIdx },
                                            shape = RoundedCornerShape(50),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (qIdx == 3) Text("⚡", color = MorphInstantBolt, fontSize = 11.sp)
                                                Text(
                                                    label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // PBI 5.2: Resolusi & FPS (khusus video → video)
                            val isVideoToVideo = (FileCategoryManager.isVideo(srcExt) || sampleFile.mimeType.startsWith("video/")) &&
                                currentTarget.lowercase() in VIDEO_TARGETS
                            if (isVideoToVideo) {
                                VideoOptionRow(
                                    title = strings.videoResolutionTitle,
                                    options = listOf(0 to strings.optionOriginal, 1080 to "1080p", 720 to "720p", 480 to "480p"),
                                    selected = resolutionPerGroup[srcExt] ?: 0,
                                    onSelect = { resolutionPerGroup[srcExt] = it }
                                )
                                VideoOptionRow(
                                    title = strings.videoFpsTitle,
                                    options = listOf(0 to strings.optionOriginal, 60 to "60fps", 30 to "30fps", 24 to "24fps"),
                                    selected = fpsPerGroup[srcExt] ?: 0,
                                    onSelect = { fpsPerGroup[srcExt] = it }
                                )
                                if (currentQuality == 3 && ((resolutionPerGroup[srcExt] ?: 0) > 0 || (fpsPerGroup[srcExt] ?: 0) > 0)) {
                                    Text(
                                        strings.videoOptionsDisableInstant,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Info Lossless/Instant
                            if (isCurrentTargetLossless) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFF3E8)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⚡", color = MorphInstantBolt, fontSize = 15.sp)
                                        Text(
                                            strings.instantLosslessSupported(srcExt, currentTarget),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8A572A)
                                        )
                                    }
                                }
                            }

                            if (!isCurrentTargetLossless && currentQuality == 3) {
                                Text(
                                    strings.instantNotAvailable,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (crossMergeGroups.isNotEmpty()) {
                val parts = crossMergeGroups.joinToString(" + ") { "${it.uppercase()} (${groupedFiles[it]?.size ?: 0})" }
                val totalPages = crossMergeGroups.sumOf { groupedFiles[it]?.size ?: 0 }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = crossMergePdf,
                                role = Role.Switch,
                                onValueChange = { crossMergePdf = it }
                            )
                            .padding(start = 11.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(strings.crossMergePdfTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                strings.crossMergePdfDesc(parts, totalPages),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // The whole row is the toggle target (bigger tap area, one a11y node), so the Switch is passive
                        Switch(checked = crossMergePdf, onCheckedChange = null)
                    }
                }
            }

            // Validasi & CTA
            // Groups with no supported target are skipped, so one .zip doesn't block a whole batch
            val allGroupsConfigured = groupedFiles.keys.any { key ->
                !targetFormatPerGroup[key].isNullOrBlank()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            ) {
                Button(
                onClick = {
                    val requests = mutableListOf<ConversionJobRequest>()

                    if (crossMergeActive) {
                        // Selection order, not group order, so pages follow the order the user picked
                        val mergeFiles = selectedFiles.filter {
                            it.fileName.substringAfterLast('.', "").trim().lowercase() in crossMergeGroups
                        }
                        val first = mergeFiles.first()
                        requests.add(
                            ConversionJobRequest(
                                inputUri = first.uri,
                                originalFileName = first.fileName.substringBeforeLast('.', "morph_merged") + ".pdf",
                                mimeType = "application/pdf",
                                targetFormat = "pdf",
                                qualityPreset = qualityPerGroup[crossMergeGroups.first()] ?: defaultQuality,
                                extraInputUris = mergeFiles.drop(1).map { it.uri }
                            )
                        )
                    }

                    groupedFiles.forEach { (srcExt, filesInGroup) ->
                        if (crossMergeActive && srcExt in crossMergeGroups) return@forEach
                        val targetFormat = targetFormatPerGroup[srcExt]?.takeIf { it.isNotBlank() } ?: return@forEach
                        val groupQuality = qualityPerGroup[srcExt] ?: defaultQuality
                        val isLossless = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetFormat, filesInGroup.first().mimeType)
                        val isVideoToVideo = (FileCategoryManager.isVideo(srcExt) || filesInGroup.first().mimeType.startsWith("video/")) &&
                            targetFormat.lowercase() in VIDEO_TARGETS
                        val groupResolution = if (isVideoToVideo) resolutionPerGroup[srcExt] ?: 0 else 0
                        val groupFps = if (isVideoToVideo) fpsPerGroup[srcExt] ?: 0 else 0
                        // Resolution/FPS changes need re-encoding, so Instant falls back to High
                        val effectiveQuality = if (groupQuality == 3 && (!isLossless || groupResolution > 0 || groupFps > 0)) 0 else groupQuality

                        val isImageToPdf = srcExt in listOf("png", "jpg", "jpeg", "webp") && targetFormat.equals("pdf", ignoreCase = true)
                        val shouldMerge = isImageToPdf && filesInGroup.size > 1 && (pdfMergeOptionPerGroup[srcExt] ?: true)

                        if (shouldMerge) {
                            val first = filesInGroup.first()
                            val extraUris = filesInGroup.drop(1).map { it.uri }
                            val baseName = first.fileName.substringBeforeLast('.', "morph_merged")
                            requests.add(
                                ConversionJobRequest(
                                    inputUri = first.uri,
                                    originalFileName = "$baseName.pdf",
                                    mimeType = "application/pdf",
                                    targetFormat = "pdf",
                                    qualityPreset = effectiveQuality,
                                    extraInputUris = extraUris
                                )
                            )
                        } else {
                            filesInGroup.forEach { file ->
                                requests.add(
                                    ConversionJobRequest(
                                        inputUri = file.uri,
                                        originalFileName = file.fileName,
                                        mimeType = file.mimeType,
                                        targetFormat = targetFormat,
                                        qualityPreset = effectiveQuality,
                                        videoResolution = groupResolution,
                                        videoFps = groupFps
                                    )
                                )
                            }
                        }
                    }

                    ConversionQueueManager.addJobs(requests)

                    val serviceIntent = Intent(context, MorphService::class.java).apply {
                        action = MorphService.ACTION_START_QUEUE
                    }
                    context.startForegroundService(serviceIntent)

                    onConvertStarted()
                },
                enabled = allGroupsConfigured,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color.White.copy(alpha = 0.55f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp
                )
            ) {
                val isMergeActive = crossMergeActive || groupedFiles.any { (srcExt, files) ->
                    srcExt in listOf("png", "jpg", "jpeg", "webp") &&
                    (targetFormatPerGroup[srcExt] ?: "").equals("pdf", ignoreCase = true) &&
                    files.size > 1 &&
                    (pdfMergeOptionPerGroup[srcExt] ?: true)
                }
                val label = when {
                    !isBatchMode -> strings.btnConvert
                    isMergeActive -> strings.btnMergeAndConvert(selectedFiles.size)
                    else -> strings.btnConvertBatch(selectedFiles.size)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("▶", fontSize = 15.sp)
                    Text(text = label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            }
        }
    }
}

private val VIDEO_TARGETS = setOf("mp4", "mkv", "mov", "webm", "avi")

/** Compact single-choice chip row for the PBI 5.2 video options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoOptionRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = {
                        Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                    },
                    shape = RoundedCornerShape(50)
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
