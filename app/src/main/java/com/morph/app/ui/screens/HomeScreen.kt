package com.morph.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morph.app.MorphEngine
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.JobStatus
import com.morph.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.ui.unit.sp

// ============================================================
// HOME SCREEN — STATE MODEL
// Sesuai MORPH_UI_UX_SPEC.md Section 7.5
// ============================================================

data class CategoryFilterItem(val id: String, val label: String)

sealed class HomeHistoryState {
    object NoHistory : HomeHistoryState()
    data class HistoryAvailable(val files: List<File>) : HomeHistoryState()
    object FilteredEmpty : HomeHistoryState()
    object Loading : HomeHistoryState()
    data class Error(val message: String) : HomeHistoryState()
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    morphFiles: List<File>,
    isRefreshing: Boolean,
    hasNotificationPermission: Boolean,
    onRefresh: () -> Unit,
    onPickFileClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onFileClick: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onDeleteFile: (File) -> Unit,
    isBatteryUnrestricted: Boolean = true,
    onRetryConversion: (android.net.Uri, String) -> Unit,
    onBannerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
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
    
    val context = androidx.compose.ui.platform.LocalContext.current
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
        isRefreshing -> HomeHistoryState.Loading
        morphFiles.isEmpty() -> HomeHistoryState.NoHistory
        filteredFiles.isEmpty() -> HomeHistoryState.FilteredEmpty
        else -> HomeHistoryState.HistoryAvailable(filteredFiles)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- 1. CONVERSION PROGRESS BANNER (jika ada job aktif) ---
            if (activeJob != null) {
                val currentJobNumber = queueState.indexOfFirst { it.id == activeJob.id } + 1
                val queueInfo = if (totalQueueCount > 1) {
                    strings.convertingBatchInfo(currentJobNumber, totalQueueCount)
                } else {
                    strings.convertingTo(activeJob.targetFormat)
                }

                item {
                    ConversionBanner(
                        format = activeJob.targetFormat,
                        progress = activeJob.progress,
                        fileName = activeJob.originalFileName,
                        queueInfo = queueInfo,
                        stepMessage = activeJob.progressMessage,
                        onCancel = { ConversionQueueManager.cancelAllJobs() },
                        onClick = onBannerClick
                    )
                }
            }

            // --- 2. ERROR BANNER ---
            if (failedJobs.isNotEmpty()) {
                failedJobs.forEach { failedJob ->
                    item(key = "failed_${failedJob.id}") {
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
            } else if (engineState is MorphEngine.ConvertState.Error) {
                val error = engineState as MorphEngine.ConvertState.Error
                item {
                    ErrorCard(
                        message = error.message,
                        onDismiss = { MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle },
                        onRetry = if (error.originalUri != null && error.targetFormat.isNotEmpty()) {
                            {
                                MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle
                                onRetryConversion(error.originalUri, error.targetFormat)
                            }
                        } else null
                    )
                }
            }

            // --- 3. NOTIFICATION PERMISSION PROMPT ---
            if (!hasNotificationPermission) {
                item {
                    NotificationPermissionBanner(onRequestPermission = onRequestPermission)
                }
            }
            
            if (!batteryUnrestricted && !batteryBannerDismissed) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.batteryBannerTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = strings.batteryBannerDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        batteryBannerDismissed = true
                                        prefs.dismissBatteryWarning = true
                                    }
                                ) {
                                    Text(strings.btnLater, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showBatteryGuideDialog = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(strings.btnLearnHow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. CATEGORY FILTER CHIPS ---
            // Hanya tampil saat ada history (populated atau filtered empty)
            if (historyState !is HomeHistoryState.NoHistory) {
                item {
                    MorphCategoryFilter(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { selectedCategoryId = it }
                    )
                }
            }

            // --- 5. MAIN CONTENT AREA ---
            when (historyState) {
                is HomeHistoryState.NoHistory -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.75f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            MorphEmptyFileState(onPickFileClick = onPickFileClick)
                        }
                    }
                }

                is HomeHistoryState.FilteredEmpty -> {
                    item {
                        val currentLabel = categories.find { it.id == selectedCategoryId }?.label ?: strings.categoryAll
                        MorphFilteredEmptyState(
                            category = currentLabel,
                            onClearFilter = { selectedCategoryId = "all" }
                        )
                    }
                }

                is HomeHistoryState.HistoryAvailable -> {
                    items(historyState.files, key = { it.absolutePath }) { file ->
                        FileHistoryItemCard(
                            file = file,
                            onClick = { onFileClick(file) },
                            onShare = { onShareFile(file) },
                            onDelete = { onDeleteFile(file) }
                        )
                    }
                }

                is HomeHistoryState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                is HomeHistoryState.Error -> {
                    item {
                        Text(
                            text = historyState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                }
                
            }   
        }

        //Battery Dialog
        if (showBatteryGuideDialog) {
            BatteryOptimizationGuideDialog(
                onDismiss = { showBatteryGuideDialog = false },
                onOpenSettings = {
                    showBatteryGuideDialog = false
                    com.morph.app.openAppBatterySettings(context, strings)
                }
            )
        }
        // --- FAB — Hanya muncul saat history tersedia ---
        if (historyState is HomeHistoryState.HistoryAvailable) {
            FloatingActionButton(
                onClick = onPickFileClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = strings.selectNewFile,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

// ============================================================
// CATEGORY FILTER CHIPS
// Sesuai MORPH_UI_UX_SPEC.md Section 7.2
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(categories, key = { it.id }) { categoryItem ->
            val isSelected = selectedCategoryId == categoryItem.id
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(categoryItem.id) },
                label = {
                    Text(
                        text = categoryItem.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    selectedBorderColor = Color.Transparent,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    selectedBorderWidth = 0.dp
                )
            )
        }
    }
}

// ============================================================
// EMPTY STATE — Circular action
// Sesuai MORPH_UI_UX_SPEC.md Section 7.3
// ============================================================

@Composable
fun MorphEmptyFileState(
    onPickFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStatePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Folder illustration
        Box(
            modifier = Modifier.align(Alignment.Center).size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 80.dp)
                    ) { onPickFileClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    // Main folder circle
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        )
                    }

                    // Plus badge
                    Box(
                        modifier = Modifier.size(40.dp).align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Text below illustration
        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = 120.dp)
                .fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val strings = AppStrings.current

            Text(
                text = strings.selectFile,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.selectFileSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// FILTERED EMPTY STATE
// Sesuai MORPH_UI_UX_SPEC.md Section 7.4 & 7.5
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
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.noFilesInCategory(category),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = strings.noFilesInCategoryDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onClearFilter,
            shape = RoundedCornerShape(14.dp),
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
// NOTIFICATION PERMISSION BANNER
// ============================================================

@Composable
fun NotificationPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.notifBannerTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = strings.notifBannerDesc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onRequestPermission) {
                Text(
                    text = strings.btnAllow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = strings.batteryDialogTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.batteryDialogIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.batteryStep1,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = strings.batteryStep2,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = strings.batteryStep3,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = strings.batteryNotes,
                    style = MaterialTheme.typography.labelSmall,
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
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(strings.btnOpenAppInfo, fontWeight = FontWeight.Bold)
            }
        }
    )
}