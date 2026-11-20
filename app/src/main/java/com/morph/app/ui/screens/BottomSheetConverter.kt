package com.morph.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morph.app.MorphService
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionCapabilityManager
import com.morph.app.core.ConversionJobRequest
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.SelectedFileItem
import com.morph.app.ui.components.FileTypeTile
import com.morph.app.ui.components.MorphCard
import com.morph.app.ui.components.MorphChoicePill
import com.morph.app.ui.components.formatFileSize
import com.morph.app.ui.components.pressScale
import com.morph.app.ui.theme.LocalMorphColors
import com.morph.app.ui.theme.MorphMotion
import com.morph.app.ui.theme.MorphSpace

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
    val scheme = MaterialTheme.colorScheme
    val morphColors = LocalMorphColors.current

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

    // Builds the queue exactly as before the redesign; only the surrounding UI changed
    val startConversion: () -> Unit = {
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
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = scheme.outline) }
    ) {
        val subtitleText = if (isBatchMode) {
            val sizeText = if (totalSize > 0) " • ${formatFileSize(totalSize)}" else ""
            strings.bottomSheetBatchSubtitle(selectedFiles.size, groupedFiles.size, sizeText)
        } else {
            val single = selectedFiles.first()
            val sizeText = if (single.sizeBytes > 0) " • ${formatFileSize(single.sizeBytes)}" else ""
            strings.bottomSheetSingleSubtitle(single.fileName, sizeText)
        }

        // --- Header: what is being converted, and a real close target ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MorphSpace.screen, end = MorphSpace.s, bottom = MorphSpace.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(MorphSpace.m))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    strings.bottomSheetTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = strings.btnClose, tint = scheme.onSurfaceVariant)
            }
        }

        // --- Scrollable options; the CTA below stays put ---
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MorphSpace.screen),
            verticalArrangement = Arrangement.spacedBy(MorphSpace.m)
        ) {
            Text(
                strings.targetFormatHeader,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface
            )

            groupedFiles.forEach { (srcExt, filesInGroup) ->
                key(srcExt) {
                    val sampleFile = filesInGroup.first()
                    val availableTargets = remember(srcExt) {
                        ConversionCapabilityManager.getTargetFormats(sampleFile.fileName, sampleFile.mimeType)
                    }
                    val currentTarget = targetFormatPerGroup[srcExt] ?: ""
                    val isCurrentTargetLossless = remember(srcExt, currentTarget) {
                        ConversionCapabilityManager.isLosslessCompatible(srcExt, currentTarget, sampleFile.mimeType)
                    }
                    val sourceCategory = FileCategoryManager.getCategory(srcExt, sampleFile.mimeType)
                    val countText = "${filesInGroup.size} file${if (filesInGroup.size > 1) strings.filesCountSuffix else ""}"

                    MorphCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Group header: source type, route, count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FileTypeTile(category = sourceCategory, size = 40.dp, iconSize = 22.dp)
                                Spacer(Modifier.width(MorphSpace.m))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        sourceCategory.getLocalizedName(strings),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = scheme.onSurface
                                    )
                                    Text(
                                        "${srcExt.uppercase()} → ${currentTarget.uppercase().ifBlank { strings.chooseFormat }} · $countText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (availableTargets.isEmpty()) {
                                Text(
                                    strings.noTargetFormatAvailable,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                    modifier = Modifier.padding(top = MorphSpace.m)
                                )
                            }

                            // Target formats, one horizontal row per target category
                            val targetsByCategory = remember(availableTargets) {
                                availableTargets.groupBy { FileCategoryManager.getCategory(it) }
                            }
                            targetsByCategory.forEach { (targetCategory, targets) ->
                                if (targetsByCategory.size > 1) {
                                    Text(
                                        targetCategory.getLocalizedName(strings),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = MorphSpace.m)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (targetsByCategory.size > 1) MorphSpace.s else MorphSpace.m)
                                        .horizontalScroll(rememberScrollState())
                                        .selectableGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(MorphSpace.s)
                                ) {
                                    targets.forEach { targetOption ->
                                        TargetFormatTile(
                                            format = targetOption,
                                            selected = currentTarget.equals(targetOption, ignoreCase = true),
                                            instant = ConversionCapabilityManager.isLosslessCompatible(srcExt, targetOption, sampleFile.mimeType),
                                            onClick = { targetFormatPerGroup[srcExt] = targetOption }
                                        )
                                    }
                                }
                            }

                            // Image group to PDF: one document or one per image
                            val isImageToPdf = srcExt in listOf("png", "jpg", "jpeg", "webp") && currentTarget.equals("pdf", ignoreCase = true)
                            SheetSection(visible = isImageToPdf && filesInGroup.size > 1 && !(crossMergeActive && srcExt in crossMergeGroups)) {
                                PdfOutputSelector(
                                    merge = pdfMergeOptionPerGroup[srcExt] ?: true,
                                    imageCount = filesInGroup.size,
                                    onChange = { pdfMergeOptionPerGroup[srcExt] = it }
                                )
                            }

                            // Quality
                            val currentQuality = qualityPerGroup[srcExt] ?: defaultQuality
                            val qualityOptions = if (isCurrentTargetLossless) {
                                listOf(Pair(strings.qualityLow, 2), Pair(strings.qualityMedium, 1), Pair(strings.qualityHigh, 0), Pair(strings.qualityInstant, 3))
                            } else {
                                listOf(Pair(strings.qualityLow, 2), Pair(strings.qualityMedium, 1), Pair(strings.qualityHigh, 0))
                            }

                            Text(
                                strings.qualityTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface,
                                modifier = Modifier.padding(top = MorphSpace.l, bottom = MorphSpace.s)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                qualityOptions.forEach { (label, qIdx) ->
                                    val isSelected = currentQuality == qIdx
                                    MorphChoicePill(
                                        selected = isSelected,
                                        onClick = { qualityPerGroup[srcExt] = qIdx },
                                        modifier = Modifier.weight(1f),
                                        horizontalPadding = 4.dp
                                    ) { contentColor ->
                                        if (qIdx == 3) {
                                            Icon(
                                                Icons.Rounded.Bolt,
                                                contentDescription = null,
                                                tint = morphColors.instantBolt,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                        }
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // PBI 5.2: resolution & FPS (video to video only)
                            val isVideoToVideo = (FileCategoryManager.isVideo(srcExt) || sampleFile.mimeType.startsWith("video/")) &&
                                currentTarget.lowercase() in VIDEO_TARGETS
                            SheetSection(visible = isVideoToVideo) {
                                Column(verticalArrangement = Arrangement.spacedBy(MorphSpace.m)) {
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
                                }
                            }
                            SheetSection(
                                visible = isVideoToVideo && currentQuality == 3 &&
                                    ((resolutionPerGroup[srcExt] ?: 0) > 0 || (fpsPerGroup[srcExt] ?: 0) > 0)
                            ) {
                                SheetNote(strings.videoOptionsDisableInstant)
                            }

                            // Instant (lossless) is available for this route
                            SheetSection(visible = isCurrentTargetLossless) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .background(morphColors.instantContainer)
                                        .padding(horizontal = MorphSpace.m, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = morphColors.instantBolt,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(MorphSpace.s))
                                    Text(
                                        strings.instantLosslessSupported(srcExt, currentTarget),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = morphColors.onInstantContainer
                                    )
                                }
                            }

                            SheetSection(visible = !isCurrentTargetLossless && currentQuality == 3) {
                                SheetNote(strings.instantNotAvailable)
                            }
                        }
                    }
                }
            }

            if (crossMergeGroups.isNotEmpty()) {
                val parts = crossMergeGroups.joinToString(" + ") { "${it.uppercase()} (${groupedFiles[it]?.size ?: 0})" }
                val totalPages = crossMergeGroups.sumOf { groupedFiles[it]?.size ?: 0 }
                MorphCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = crossMergePdf,
                                role = Role.Switch,
                                onValueChange = { crossMergePdf = it }
                            )
                            .padding(start = 14.dp, end = MorphSpace.m, top = MorphSpace.m, bottom = MorphSpace.m),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(strings.crossMergePdfTitle, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                            Text(
                                strings.crossMergePdfDesc(parts, totalPages),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(MorphSpace.m))
                        // The whole row is the toggle target (bigger tap area, one a11y node), so the Switch is passive
                        Switch(checked = crossMergePdf, onCheckedChange = null)
                    }
                }
            }
        }

        // --- CTA ---
        // Groups with no supported target are skipped, so one .zip doesn't block a whole batch
        val allGroupsConfigured = groupedFiles.keys.any { key ->
            !targetFormatPerGroup[key].isNullOrBlank()
        }
        val isMergeActive = crossMergeActive || groupedFiles.any { (srcExt, files) ->
            srcExt in listOf("png", "jpg", "jpeg", "webp") &&
                (targetFormatPerGroup[srcExt] ?: "").equals("pdf", ignoreCase = true) &&
                files.size > 1 &&
                (pdfMergeOptionPerGroup[srcExt] ?: true)
        }
        val ctaLabel = when {
            !isBatchMode -> strings.btnConvert
            isMergeActive -> strings.btnMergeAndConvert(selectedFiles.size)
            else -> strings.btnConvertBatch(selectedFiles.size)
        }
        val ctaInteraction = remember { MutableInteractionSource() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MorphSpace.screen, end = MorphSpace.screen, top = MorphSpace.m, bottom = MorphSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = startConversion,
                enabled = allGroupsConfigured,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(ctaInteraction, 0.98f),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                ),
                interactionSource = ctaInteraction
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(MorphSpace.s))
                Text(text = ctaLabel, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                strings.convertOnDeviceNote,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MorphSpace.s)
            )
        }
    }
}

private val VIDEO_TARGETS = setOf("mp4", "mkv", "mov", "webm", "avi")

/** Conditional sheet sections fade and unfold in place instead of popping in. */
@Composable
private fun SheetSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MorphMotion.medium()) + expandVertically(MorphMotion.medium()),
        exit = fadeOut(MorphMotion.fast()) + shrinkVertically(MorphMotion.medium())
    ) {
        Box(modifier = Modifier.padding(top = MorphSpace.m)) { content() }
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/** One target format: type tile + extension; a yellow bolt marks routes that can run Instant. */
@Composable
private fun TargetFormatTile(
    format: String,
    selected: Boolean,
    instant: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val morphColors = LocalMorphColors.current
    val strings = AppStrings.current
    val shape = RoundedCornerShape(14.dp)
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        MorphMotion.fast(),
        label = "tileContainer"
    )
    val border by animateColorAsState(
        if (selected) scheme.primary else scheme.outlineVariant,
        MorphMotion.fast(),
        label = "tileBorder"
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(76.dp)
            .pressScale(interaction, 0.96f)
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = ripple()
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FileTypeTile(
                category = FileCategoryManager.getCategory(format),
                size = 32.dp,
                iconSize = 18.dp,
                cornerRadius = 9.dp
            )
            Text(
                format.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                maxLines = 1
            )
        }
        if (instant) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
                    .background(morphColors.instantContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = strings.instantSupportedLabel,
                    tint = morphColors.instantBolt,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** Merge images into one PDF or write one PDF per image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfOutputSelector(
    merge: Boolean,
    imageCount: Int,
    onChange: (Boolean) -> Unit
) {
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = scheme.primaryContainer,
        activeContentColor = scheme.onPrimaryContainer,
        activeBorderColor = scheme.primary,
        inactiveContainerColor = LocalMorphColors.current.card,
        inactiveContentColor = scheme.onSurfaceVariant,
        inactiveBorderColor = scheme.outlineVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(MorphSpace.s)) {
        Text(strings.pdfOutputTitle, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = merge,
                onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = colors
            ) {
                Text(strings.pdfModeMerge, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            SegmentedButton(
                selected = !merge,
                onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = colors
            ) {
                Text(strings.pdfModeSeparate, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Text(
            if (merge) strings.pdfModeMergeDesc(imageCount) else strings.pdfModeSeparateDesc,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

/** Single-choice pill row for the PBI 5.2 video options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoOptionRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MorphSpace.s)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                MorphChoicePill(
                    selected = isSelected,
                    onClick = { onSelect(value) }
                ) { contentColor ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor
                    )
                }
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
