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
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.JobStatus
import com.morph.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ============================================================
// HOME SCREEN — STATE MODEL
// Sesuai MORPH_UI_UX_SPEC.md Section 7.5
// ============================================================

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
    onRetryConversion: (android.net.Uri, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
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
    // Urutan sesuai spec: Semua, Dokumen, Gambar, Video, Audio
    var selectedCategory by remember { mutableStateOf("Semua") }
    val categories = listOf("Semua", "Dokumen", "Gambar", "Video", "Audio")

    val filteredFiles = remember(morphFiles, selectedCategory) {
        if (selectedCategory == "Semua") {
            morphFiles
        } else {
            morphFiles.filter { file ->
                val cat = FileCategoryManager.getCategory(file.extension)
                cat.displayName.equals(selectedCategory, ignoreCase = true)
            }
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
                    "Mengonversi ($currentJobNumber/$totalQueueCount)"
                } else {
                    "Mengonversi ke ${activeJob.targetFormat.uppercase()}"
                }

                item {
                    ConversionBanner(
                        format = activeJob.targetFormat,
                        progress = activeJob.progress,
                        fileName = activeJob.originalFileName,
                        queueInfo = queueInfo,
                        stepMessage = activeJob.progressMessage,
                        onCancel = { ConversionQueueManager.cancelAllJobs() }
                    )
                }
            }

            // --- 2. ERROR BANNER ---
            if (failedJobs.isNotEmpty()) {
                failedJobs.forEach { failedJob ->
                    item(key = "failed_${failedJob.id}") {
                        ErrorCard(
                            message = "${failedJob.originalFileName}: ${failedJob.errorMessage ?: "Gagal dikonversi"}",
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

            // --- 4. CATEGORY FILTER CHIPS ---
            // Hanya tampil saat ada history (populated atau filtered empty)
            if (historyState !is HomeHistoryState.NoHistory) {
                item {
                    MorphCategoryFilter(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
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
                        MorphFilteredEmptyState(
                            category = selectedCategory,
                            onClearFilter = { selectedCategory = "Semua" }
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
                    contentDescription = "Pilih File Baru",
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
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = category,
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
    // Pulse animation untuk outer ring
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStatePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circular action button — 124dp sesuai spec 112-136dp
        Box(
            modifier = Modifier
                .size(124.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
                .clickable { onPickFileClick() },
            contentAlignment = Alignment.Center
        ) {
            // Inner circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                // Folder icon
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )

                // Plus badge — pojok kanan bawah
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Pilih File",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pilih file dari perangkat untuk mulai mengonversi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
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
            text = "Tidak ada file $category",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Belum ada file dalam kategori ini. Coba kategori lain atau konversi file baru.",
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
                text = "Tampilkan Semua",
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
                    text = "Aktifkan Notifikasi",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Agar konversi latar belakang tidak dihentikan sistem.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onRequestPermission) {
                Text(
                    text = "Izinkan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
