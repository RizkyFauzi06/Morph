package com.morph.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.morph.app.MorphEngine
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.JobStatus
import com.morph.app.core.MorphOutputFile
import com.morph.app.ui.components.*
import com.morph.app.ui.theme.LocalMorphColors
import com.morph.app.ui.theme.LocalReduceMotion
import com.morph.app.ui.theme.MorphMotion
import com.morph.app.ui.theme.MorphSpace
import kotlinx.coroutines.delay

// ============================================================
// HOME SCREEN: STATE MODEL (MORPH_UI_UX_SPEC.md section 7.5)
// ============================================================

data class CategoryFilterItem(val id: String, val label: String)

sealed class HomeHistoryState {
    object NoHistory : HomeHistoryState()
    data class HistoryAvailable(val files: List<MorphOutputFile>) : HomeHistoryState()
    object FilteredEmpty : HomeHistoryState()
    object Loading : HomeHistoryState()
    data class Error(val message: String) : HomeHistoryState()
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    morphFiles: List<MorphOutputFile>,
    isRefreshing: Boolean,
    hasNotificationPermission: Boolean,
    onRefresh: () -> Unit,
    onPickFileClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onFileClick: (MorphOutputFile) -> Unit,
    onShareFile: (MorphOutputFile) -> Unit,
    onDeleteFile: (MorphOutputFile) -> Unit,
    onRetryConversion: (android.net.Uri, String) -> Unit,
    modifier: Modifier = Modifier,
    isBatteryUnrestricted: Boolean = true,
    onBannerClick: () -> Unit = {}
) {
    val strings = AppStrings.current

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { onRefresh() }
    )

    val engineState by MorphEngine.currentStatus.collectAsState()
    val queueState by ConversionQueueManager.queueState.collectAsState()

    val activeJob = remember(queueState) { queueState.firstOrNull { it.status == JobStatus.PROCESSING } }
    val failedJobs = remember(queueState) { queueState.filter { it.status == JobStatus.FAILED } }
    val completedCount = remember(queueState) { queueState.count { it.status == JobStatus.COMPLETED } }
    val totalQueueCount = remember(queueState) { queueState.size }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val prefs = remember { com.morph.app.MorphPrefs(context) }

    var batteryUnrestricted by remember {
        mutableStateOf(com.morph.app.checkBatteryOptimizationDisabled(context))
    }
    var batteryBannerDismissed by remember { mutableStateOf(prefs.dismissBatteryWarning) }
    var showBatteryGuideDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = com.morph.app.checkBatteryOptimizationDisabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-refresh when conversion completes
    LaunchedEffect(engineState, completedCount) {
        if (engineState is MorphEngine.ConvertState.Success || completedCount > 0) {
            onRefresh()
            if (engineState is MorphEngine.ConvertState.Success) {
                delay(1500)
                MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle
            }
        }
    }

    // --- FILTER STATE ---
    var selectedCategoryId by remember { mutableStateOf("all") }
    val categories = listOf(
        CategoryFilterItem("all", strings.categoryAll),
        CategoryFilterItem("document", strings.categoryDocument),
        CategoryFilterItem("image", strings.categoryImage),
        CategoryFilterItem("video", strings.categoryVideo),
        CategoryFilterItem("audio", strings.categoryAudio)
    )

    val filteredFiles = remember(morphFiles, selectedCategoryId) {
        when (selectedCategoryId) {
            "all" -> morphFiles
            "document" -> morphFiles.filter {
                val c = FileCategoryManager.getCategory(it.extension)
                c == FileCategory.DOCUMENT || c == FileCategory.PDF || c == FileCategory.EBOOK || c == FileCategory.SPREADSHEET
            }
            "image" -> morphFiles.filter { FileCategoryManager.getCategory(it.extension) == FileCategory.IMAGE }
            "video" -> morphFiles.filter { FileCategoryManager.getCategory(it.extension) == FileCategory.VIDEO }
            "audio" -> morphFiles.filter { FileCategoryManager.getCategory(it.extension) == FileCategory.AUDIO }
            else -> morphFiles
        }
    }

    // --- HISTORY STATE MODEL ---
    val historyState: HomeHistoryState = when {
        // Refresh is async now; keep showing the current list instead of flashing a spinner
        morphFiles.isEmpty() && isRefreshing -> HomeHistoryState.Loading
        morphFiles.isEmpty() -> HomeHistoryState.NoHistory
        filteredFiles.isEmpty() -> HomeHistoryState.FilteredEmpty
        else -> HomeHistoryState.HistoryAvailable(filteredFiles)
    }

    // --- UPDATE NOTICE ---
    var availableRelease by remember { mutableStateOf<com.morph.app.core.ReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (prefs.autoCheckUpdates) {
            val result = com.morph.app.core.UpdateChecker.checkForUpdates(context)
            if (result is com.morph.app.core.UpdateCheckResult.UpdateAvailable &&
                result.release.tagName != prefs.dismissedUpdateTag
            ) {
                availableRelease = result.release
            }
        }
    }

    val engineError = engineState as? MorphEngine.ConvertState.Error
    val showBatteryNotice = !batteryUnrestricted && !batteryBannerDismissed
    val hasNotices = activeJob != null || failedJobs.isNotEmpty() || engineError != null ||
        !hasNotificationPermission || showBatteryNotice || availableRelease != null

    // Live job, failures and one-time prompts share one column above the content
    val notices: @Composable (Modifier) -> Unit = { noticeModifier ->
        Column(
            modifier = noticeModifier
                .fillMaxWidth()
                .animateContentSize(MorphMotion.sizeSpring),
            verticalArrangement = Arrangement.spacedBy(MorphSpace.m)
        ) {
            NoticeVisibility(visible = activeJob != null) {
                val job = activeJob ?: return@NoticeVisibility
                val currentJobNumber = queueState.indexOfFirst { it.id == job.id } + 1
                ConversionBanner(
                    format = job.targetFormat,
                    progress = job.progress,
                    fileName = job.originalFileName,
                    queueInfo = if (totalQueueCount > 1) {
                        strings.convertingBatchInfo(currentJobNumber, totalQueueCount)
                    } else {
                        strings.convertingTo(job.targetFormat)
                    },
                    stepMessage = job.progressMessage,
                    onCancel = { ConversionQueueManager.cancelAllJobs() },
                    onClick = onBannerClick
                )
            }

            if (failedJobs.isNotEmpty()) {
                failedJobs.forEach { failedJob ->
                    key(failedJob.id) {
                        ErrorCard(
                            message = "${failedJob.originalFileName}: ${failedJob.errorMessage ?: strings.failedToConvertDefault}",
                            onDismiss = { ConversionQueueManager.dismissJob(failedJob.id) },
                            onRetry = {
                                ConversionQueueManager.dismissJob(failedJob.id)
                                onRetryConversion(failedJob.inputUri, failedJob.targetFormat)
                            }
                        )
                    }
                }
            } else if (engineError != null) {
                ErrorCard(
                    message = engineError.message,
                    onDismiss = { MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle },
                    onRetry = if (engineError.originalUri != null && engineError.targetFormat.isNotEmpty()) {
                        {
                            MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle
                            onRetryConversion(engineError.originalUri, engineError.targetFormat)
                        }
                    } else null
                )
            }

            NoticeVisibility(visible = !hasNotificationPermission) {
                NotificationPermissionBanner(onRequestPermission = onRequestPermission)
            }

            NoticeVisibility(visible = showBatteryNotice) {
                MorphNoticeCard(
                    icon = Icons.Rounded.BatteryAlert,
                    title = strings.batteryBannerTitle,
                    description = strings.batteryBannerDesc
                ) {
                    TextButton(onClick = {
                        batteryBannerDismissed = true
                        prefs.dismissBatteryWarning = true
                    }) {
                        Text(strings.btnLater)
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = { showBatteryGuideDialog = true },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(strings.btnLearnHow, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            NoticeVisibility(visible = availableRelease != null) {
                val release = availableRelease ?: return@NoticeVisibility
                MorphNoticeCard(
                    icon = Icons.Rounded.SystemUpdate,
                    title = strings.updateAvailableTitle(release.tagName),
                    description = strings.updateAvailableDesc,
                    tone = NoticeTone.Accent,
                    onClick = { showUpdateDialog = true }
                ) {
                    TextButton(onClick = {
                        prefs.dismissedUpdateTag = release.tagName
                        availableRelease = null
                    }) {
                        Text(strings.btnLater, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { showUpdateDialog = true },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(strings.btnDownload, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        when (historyState) {
            is HomeHistoryState.HistoryAvailable, is HomeHistoryState.FilteredEmpty -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = MorphSpace.screen,
                        end = MorphSpace.screen,
                        top = MorphSpace.s,
                        bottom = 104.dp // clears the FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasNotices) {
                        item(key = "notices", contentType = "notices") {
                            notices(Modifier.animateItem().padding(bottom = 6.dp))
                        }
                    }

                    item(key = "filters", contentType = "filters") {
                        MorphCategoryFilter(
                            categories = categories,
                            selectedCategoryId = selectedCategoryId,
                            onCategorySelected = { selectedCategoryId = it }
                        )
                    }

                    item(key = "history_header", contentType = "header") {
                        MorphSectionLabel(
                            text = strings.historyTitle,
                            trailing = strings.historyCount(filteredFiles.size),
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    if (historyState is HomeHistoryState.HistoryAvailable) {
                        items(
                            items = historyState.files,
                            key = { it.uri.toString() },
                            contentType = { "file" }
                        ) { file ->
                            FileHistoryItemCard(
                                file = file,
                                onClick = { onFileClick(file) },
                                onShare = { onShareFile(file) },
                                onDelete = { onDeleteFile(file) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = MorphMotion.medium(),
                                    placementSpec = MorphMotion.medium(),
                                    fadeOutSpec = MorphMotion.fast()
                                )
                            )
                        }
                    } else {
                        item(key = "filtered_empty", contentType = "empty") {
                            val currentLabel = categories.find { it.id == selectedCategoryId }?.label ?: strings.categoryAll
                            MorphFilteredEmptyState(
                                category = currentLabel,
                                onClearFilter = { selectedCategoryId = "all" },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            else -> {
                // No history yet: the pick-file circle sits centred in the space under any notices.
                // Kept outside LazyColumn so its looping pulse never lives inside a lazy item.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MorphSpace.screen, vertical = MorphSpace.s)
                ) {
                    if (hasNotices) notices(Modifier)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = MorphSpace.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        when (historyState) {
                            is HomeHistoryState.Loading -> CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            is HomeHistoryState.Error -> Text(
                                text = historyState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            else -> MorphEmptyFileState(onPickFileClick = onPickFileClick)
                        }
                    }
                }
            }
        }

        // --- FAB: only once there is history to sit on top of ---
        val fabInteraction = remember { MutableInteractionSource() }
        AnimatedVisibility(
            visible = historyState is HomeHistoryState.HistoryAvailable || historyState is HomeHistoryState.FilteredEmpty,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MorphSpace.screen, bottom = MorphSpace.screen),
            enter = scaleIn(MorphMotion.medium(), initialScale = 0.6f) + fadeIn(MorphMotion.medium()),
            exit = scaleOut(MorphMotion.fast(), targetScale = 0.6f) + fadeOut(MorphMotion.fast())
        ) {
            FloatingActionButton(
                onClick = onPickFileClick,
                modifier = Modifier.pressScale(fabInteraction, 0.96f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                ),
                interactionSource = fabInteraction
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = strings.selectNewFile,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = LocalMorphColors.current.card,
            contentColor = MaterialTheme.colorScheme.primary
        )

        if (showBatteryGuideDialog) {
            BatteryOptimizationGuideDialog(
                onDismiss = { showBatteryGuideDialog = false },
                onOpenSettings = {
                    showBatteryGuideDialog = false
                    com.morph.app.openAppBatterySettings(context, strings)
                }
            )
        }

        if (showUpdateDialog && availableRelease != null) {
            AppUpdateDialog(
                release = availableRelease!!,
                strings = strings,
                onDismiss = { showUpdateDialog = false },
                onDownloadClick = {
                    showUpdateDialog = false
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        availableRelease!!.htmlUrl.toUri()
                    )
                    context.startActivity(intent)
                }
            )
        }
    }
}

/** Notices slide in and out instead of popping, so the list below never jumps. */
@Composable
private fun NoticeVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MorphMotion.medium()) + expandVertically(MorphMotion.medium()),
        exit = fadeOut(MorphMotion.fast()) + shrinkVertically(MorphMotion.medium())
    ) {
        content()
    }
}

// ============================================================
// CATEGORY FILTER (spec 7.2): selected = filled violet, others = card with hairline
// ============================================================

@Composable
fun MorphCategoryFilter(
    categories: List<CategoryFilterItem>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MorphSpace.s),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(categories, key = { it.id }) { categoryItem ->
            val isSelected = selectedCategoryId == categoryItem.id
            MorphChoicePill(
                selected = isSelected,
                onClick = { onCategorySelected(categoryItem.id) },
                style = PillStyle.Filled,
                height = 40.dp
            ) { contentColor ->
                Text(
                    text = categoryItem.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}

// ============================================================
// EMPTY STATE (spec 7.3): one tappable circle, a soft ring that breathes, one line of help
// ============================================================

@Composable
fun MorphEmptyFileState(
    onPickFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current

    // Read only inside graphicsLayer: the loop redraws, it never recomposes or relayouts
    val pulse: () -> Float = if (reduceMotion) {
        { 0f }
    } else {
        val transition = rememberInfiniteTransition(label = "emptyPulse")
        val fraction = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emptyPulseFraction"
        )
        val read: () -> Float = { fraction.value }
        read
    }
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.padding(horizontal = MorphSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(184.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        val f = pulse()
                        val s = 1f + 0.05f * f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.45f * f
                    }
                    .border(BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.28f)), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .pressScale(interaction, 0.97f)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onPickFileClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(60.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = strings.selectNewFile,
                        tint = scheme.primary,
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.Center)
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd)
                            .background(scheme.primary, CircleShape)
                            .border(2.dp, scheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(MorphSpace.l))
        Text(
            text = strings.selectFile,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.selectFileSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ============================================================
// FILTERED EMPTY STATE (spec 7.4 / 7.5)
// ============================================================

@Composable
fun MorphFilteredEmptyState(
    category: String,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = MorphSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.height(MorphSpace.l))
        Text(
            text = strings.noFilesInCategory(category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.noFilesInCategoryDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MorphSpace.l))
        OutlinedButton(
            onClick = onClearFilter,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = strings.btnShowAll,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============================================================
// NOTIFICATION PERMISSION NOTICE
// ============================================================

@Composable
fun NotificationPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current
    MorphNoticeCard(
        icon = Icons.Rounded.NotificationsActive,
        title = strings.notifBannerTitle,
        description = strings.notifBannerDesc,
        modifier = modifier
    ) {
        FilledTonalButton(
            onClick = onRequestPermission,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(strings.btnAllow, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun BatteryOptimizationGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val strings = AppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = LocalMorphColors.current.card,
        title = {
            Text(
                text = strings.batteryDialogTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MorphSpace.m)
            ) {
                Text(
                    text = strings.batteryDialogIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(MorphSpace.m),
                        verticalArrangement = Arrangement.spacedBy(MorphSpace.s)
                    ) {
                        Text(strings.batteryStep1, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.batteryStep2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = strings.batteryStep3,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = strings.batteryNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.btnClose)
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings, shape = MaterialTheme.shapes.medium) {
                Text(strings.btnOpenAppInfo, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun AppUpdateDialog(
    release: com.morph.app.core.ReleaseInfo,
    strings: com.morph.app.core.Strings,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = LocalMorphColors.current.card,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = strings.updateDialogTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${release.tagName} · ${release.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MorphSpace.s)
            ) {
                Text(
                    text = release.releaseNotes.ifBlank { strings.updateDefaultReleaseNotes },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.btnLater)
            }
        },
        confirmButton = {
            Button(onClick = onDownloadClick, shape = MaterialTheme.shapes.medium) {
                Text(text = strings.btnDownloadApk, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
