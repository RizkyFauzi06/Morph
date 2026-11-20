package com.morph.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.JobStatus
import com.morph.app.ui.components.FileTypeTile
import com.morph.app.ui.components.MorphLogo
import com.morph.app.ui.theme.LocalMorphColors
import com.morph.app.ui.theme.LocalReduceMotion
import com.morph.app.ui.theme.MorphMotion
import com.morph.app.ui.theme.MorphSpace

private enum class ProgressPhase { Running, Success, Failed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onViewResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current
    val queueState by ConversionQueueManager.queueState.collectAsState()

    val totalCount = queueState.size
    val completedCount = queueState.count { it.status == JobStatus.COMPLETED }
    val failedCount = queueState.count { it.status == JobStatus.FAILED }
    val activeJob = queueState.firstOrNull { it.status == JobStatus.PROCESSING }

    val isFinished = totalCount > 0 && queueState.none { it.status == JobStatus.PROCESSING || it.status == JobStatus.WAITING }
    val isAllSuccess = isFinished && failedCount == 0
    val phase = when {
        !isFinished -> ProgressPhase.Running
        isAllSuccess -> ProgressPhase.Success
        else -> ProgressPhase.Failed
    }

    // Konfirmasi pembatalan
    var showCancelDialog by remember { mutableStateOf(false) }

    // Intercept back button to minimize
    BackHandler {
        onMinimize()
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = LocalMorphColors.current.card,
            title = { Text(strings.cancelDialogTitle, style = MaterialTheme.typography.titleLarge) },
            text = { Text(strings.cancelDialogDesc, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        ConversionQueueManager.cancelAllJobs()
                        onFinish()
                    }
                ) {
                    Text(strings.btnConfirmCancel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(strings.btnContinue)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isFinished) strings.titleFinished else strings.titleConversionProgress,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMinimize) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.btnMinimize
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedContent(
            targetState = phase,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                (fadeIn(MorphMotion.slow()) + scaleIn(MorphMotion.slow(), initialScale = 0.96f)) togetherWith
                    fadeOut(MorphMotion.fast())
            },
            label = "progressPhase"
        ) { target ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MorphSpace.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (target) {
                    ProgressPhase.Running -> {
                        val currentJob = activeJob ?: queueState.firstOrNull()
                        val currentPercent = (currentJob?.progress ?: 0).coerceIn(0, 100)
                        val animatedProgress by animateFloatAsState(
                            targetValue = currentPercent / 100f,
                            animationSpec = MorphMotion.slow(),
                            label = "progressAnim"
                        )

                        Spacer(Modifier.weight(1f))
                        FileTransformationVisual(
                            sourceExt = currentJob?.originalFileName?.substringAfterLast('.', "") ?: "",
                            targetExt = currentJob?.targetFormat ?: ""
                        )
                        Spacer(Modifier.height(MorphSpace.xxl))

                        Text(
                            text = strings.convertingFileProgress,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(MorphSpace.s))
                        // Real percentage from the engine, never a simulated one (spec 10)
                        Text(
                            text = "$currentPercent%",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(MorphSpace.l))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(Modifier.height(MorphSpace.m))
                        if (totalCount > 1) {
                            Text(
                                text = strings.progressBatchFilesCompleted(completedCount, totalCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentJob != null) {
                            Text(
                                text = strings.processingFile(currentJob.originalFileName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(Modifier.weight(1.2f))
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            contentPadding = PaddingValues(horizontal = MorphSpace.xl)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(MorphSpace.s))
                            Text(strings.cancelConversion, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    ProgressPhase.Success -> {
                        Spacer(Modifier.weight(1f))
                        ResultBadge(success = true, contentDescription = strings.titleFinished)
                        Spacer(Modifier.height(MorphSpace.xl))
                        Text(
                            text = strings.conversionSuccess,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(MorphSpace.s))
                        Text(
                            text = if (totalCount == 1) strings.singleFileSuccessDesc else strings.batchFilesSuccessDesc(completedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // HFX-5: per-file result details (e.g. PDF size before → after)
                        val resultDetails = queueState.mapNotNull { job ->
                            job.resultDetail?.let { if (totalCount == 1) it else "${job.originalFileName}: $it" }
                        }
                        if (resultDetails.isNotEmpty()) {
                            Text(
                                text = resultDetails.joinToString("\n"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = MorphSpace.m)
                            )
                        }

                        Spacer(Modifier.weight(1.2f))
                        Button(
                            onClick = onViewResult,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(MorphSpace.s))
                            Text(strings.btnViewResult, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(MorphSpace.m))
                        OutlinedButton(
                            onClick = onFinish,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(MorphSpace.s))
                            Text(strings.btnBackToHome, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    ProgressPhase.Failed -> {
                        Spacer(Modifier.weight(1f))
                        ResultBadge(success = false, contentDescription = null)
                        Spacer(Modifier.height(MorphSpace.xl))
                        Text(
                            text = if (completedCount > 0) strings.conversionPartialSuccess else strings.conversionFailed,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(MorphSpace.s))
                        val summaryText = if (completedCount > 0) {
                            strings.summaryPartial(completedCount, failedCount)
                        } else {
                            queueState.firstOrNull { it.status == JobStatus.FAILED }?.errorMessage ?: strings.defaultErrorMsg
                        }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.weight(1.2f))
                        Button(
                            onClick = onFinish,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(MorphSpace.s))
                            Text(strings.btnBackToHome, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Spacer(Modifier.height(MorphSpace.xl))
            }
        }
    }
}

/** Success check (or error mark) that settles into place once, without a bounce. */
@Composable
private fun ResultBadge(success: Boolean, contentDescription: String?) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        modifier = Modifier
            .size(136.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(if (success) scheme.primaryContainer else scheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (success) scheme.primary else scheme.error),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                contentDescription = contentDescription,
                tint = if (success) scheme.onPrimary else scheme.onError,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

/** Source tile → Morph → target tile; the two tiles drift in opposite directions while work runs. */
@Composable
private fun FileTransformationVisual(
    sourceExt: String,
    targetExt: String
) {
    val reduceMotion = LocalReduceMotion.current
    // Read only in graphicsLayer, so the loop costs a redraw per frame, never a relayout
    val drift: () -> Float = if (reduceMotion) {
        { 0f }
    } else {
        val transition = rememberInfiniteTransition(label = "transformDrift")
        val value = transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "drift"
        )
        val read: () -> Float = { value.value }
        read
    }
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .border(1.5.dp, ringColor, CircleShape)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransformTile(
                ext = sourceExt,
                modifier = Modifier.graphicsLayer {
                    translationY = drift() * 6.dp.toPx()
                }
            )
            MorphLogo(size = 64.dp)
            TransformTile(
                ext = targetExt,
                modifier = Modifier.graphicsLayer {
                    translationY = -drift() * 6.dp.toPx()
                }
            )
        }
    }
}

@Composable
private fun TransformTile(ext: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MorphSpace.s)
    ) {
        FileTypeTile(
            category = FileCategoryManager.getCategory(ext),
            size = 68.dp,
            iconSize = 32.dp,
            cornerRadius = 18.dp
        )
        Text(
            text = ext.uppercase().ifBlank { "FILE" },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
