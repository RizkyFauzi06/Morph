package com.morph.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morph.app.core.AppStrings
import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.MorphOutputFile
import com.morph.app.ui.theme.LocalMorphColors
import com.morph.app.ui.theme.MorphMotion
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

// ============================================================
// FILE TYPE TILE: Morph's identity motif. The same rounded, category-tinted tile marks a file
// type in History, the converter sheet, Tools and the progress screen.
// ============================================================

/** One icon family (Material Rounded) for every file category. */
fun fileCategoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGE -> Icons.Rounded.Image
    FileCategory.VIDEO -> Icons.Rounded.VideoFile
    FileCategory.AUDIO -> Icons.Rounded.MusicNote
    FileCategory.PDF -> Icons.Rounded.PictureAsPdf
    FileCategory.EBOOK -> Icons.AutoMirrored.Rounded.MenuBook
    FileCategory.DOCUMENT -> Icons.Rounded.Description
    FileCategory.SPREADSHEET -> Icons.Rounded.TableChart
    FileCategory.ARCHIVE -> Icons.Rounded.FolderZip
    FileCategory.CODE -> Icons.Rounded.Code
    FileCategory.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

@Composable
fun FileTypeTile(
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    cornerRadius: Dp = 12.dp,
    icon: ImageVector = fileCategoryIcon(category)
) {
    val colors = FileCategoryManager.getCategoryColors(category, LocalMorphColors.current.isDark)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // the file name or label next to it carries the meaning
            tint = colors.icon,
            modifier = Modifier.size(iconSize)
        )
    }
}

// ============================================================
// SURFACES
// ============================================================

/** Flat card: white (dark: one step above background) with a hairline instead of a shadow. */
@Composable
fun MorphCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = LocalMorphColors.current.card,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, content = content)
    }
}

/** Small press feedback (spec 13: 0.96-0.98) that stays in the draw phase, so no relayout per frame. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MorphMotion.fast(), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ============================================================
// TOP BAR & BOTTOM NAVIGATION (spec 5.1 / 7.1)
// ============================================================

/** One bar for all tabs so the content never jumps: the Morph wordmark on Home, a title elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphTopBar(
    title: String?,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(MorphMotion.medium()) togetherWith fadeOut(MorphMotion.fast()) },
                label = "topBarTitle"
            ) { current ->
                if (current == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MorphLogo(size = 32.dp)
                        Text(
                            text = "Morph",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Text(
                        text = current,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

data class MorphNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun MorphBottomNavigation(
    items: List<MorphNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = LocalMorphColors.current.card,
            tonalElevation = 0.dp
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = null // the label below names it
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// ============================================================
// CHOICE PILL: filters, quality, resolution, FPS. Selection reads through fill AND border
// (spec 16: never color alone), and animates in 180 ms.
// ============================================================

enum class PillStyle { Filled, Tonal }

@Composable
fun MorphChoicePill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Tonal,
    role: Role = Role.RadioButton,
    height: Dp = 44.dp,
    horizontalPadding: Dp = 14.dp,
    content: @Composable RowScope.(contentColor: Color) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val card = LocalMorphColors.current.card
    val targetContainer = when {
        selected && style == PillStyle.Filled -> scheme.primary
        selected -> scheme.primaryContainer
        style == PillStyle.Filled -> card
        else -> scheme.surfaceContainer
    }
    val targetBorder = when {
        selected && style == PillStyle.Tonal -> scheme.primary
        selected -> scheme.primary
        style == PillStyle.Filled -> scheme.outlineVariant
        else -> Color.Transparent
    }
    val targetContent = when {
        selected && style == PillStyle.Filled -> scheme.onPrimary
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    val container by animateColorAsState(targetContainer, MorphMotion.fast(), label = "pillContainer")
    val border by animateColorAsState(targetBorder, MorphMotion.fast(), label = "pillBorder")
    val contentColor by animateColorAsState(targetContent, MorphMotion.fast(), label = "pillContent")
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(height)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, border, CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = role,
                interactionSource = interaction,
                indication = ripple()
            )
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content(contentColor)
    }
}

// ============================================================
// NOTICE CARD: permission, battery, update and error prompts share one shape
// ============================================================

enum class NoticeTone { Neutral, Accent, Error }

@Composable
fun MorphNoticeCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Neutral,
    onClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val (container, iconTint, iconBg, titleColor, bodyColor) = when (tone) {
        NoticeTone.Neutral -> NoticeColors(LocalMorphColors.current.card, scheme.primary, scheme.primaryContainer, scheme.onSurface, scheme.onSurfaceVariant)
        NoticeTone.Accent -> NoticeColors(scheme.primaryContainer, scheme.primary, LocalMorphColors.current.card, scheme.onPrimaryContainer, scheme.onPrimaryContainer)
        NoticeTone.Error -> NoticeColors(scheme.errorContainer, scheme.error, LocalMorphColors.current.card, scheme.onErrorContainer, scheme.onErrorContainer)
    }
    MorphCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        color = container,
        borderColor = if (tone == NoticeTone.Neutral) scheme.outlineVariant else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
                }
            }
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

private data class NoticeColors(
    val container: Color,
    val iconTint: Color,
    val iconBg: Color,
    val title: Color,
    val body: Color
)

// --------------------------------------------------
// LIVE CONVERSION BANNER: the one focal card while a job runs
// --------------------------------------------------

@Composable
fun ConversionBanner(
    format: String,
    progress: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    queueInfo: String? = null,
    stepMessage: String? = null,
    onClick: (() -> Unit)? = null
) {
    val safeProgress = progress.coerceIn(0, 100)
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress / 100f,
        animationSpec = MorphMotion.slow(),
        label = "bannerProgress"
    )

    MorphCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        color = scheme.primaryContainer,
        borderColor = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeTile(category = FileCategoryManager.getCategory(format), size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = queueInfo ?: strings.convertingTo(format),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (fileName != null) "$fileName → ${format.uppercase()}" else strings.conversionInProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.cancelConversion, tint = scheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(LocalMorphColors.current.card)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stepMessage ?: strings.runningInBackground,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$safeProgress%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary
                )
            }
        }
    }
}

// --------------------------------------------------
// ERROR & RETRY CARD
// --------------------------------------------------

@Composable
fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val strings = AppStrings.current
    MorphNoticeCard(
        icon = Icons.Rounded.ErrorOutline,
        title = strings.conversionFailed,
        description = message,
        tone = NoticeTone.Error,
        modifier = modifier
    ) {
        TextButton(onClick = onDismiss) {
            Text(strings.btnClose, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        if (onRetry != null) {
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onRetry,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.btnRetry, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// --------------------------------------------------
// HISTORY ROW (spec 7.4): type tile, name, "size · EXT", real actions only
// --------------------------------------------------

@Composable
fun FileHistoryItemCard(
    file: MorphOutputFile,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = LocalMorphColors.current.card,
            title = { Text(strings.deleteFileDialogTitle, style = MaterialTheme.typography.titleLarge) },
            text = { Text(strings.deleteFileDialogMessage(file.name), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(strings.btnDelete, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(strings.btnCancel) }
            }
        )
    }

    val interaction = remember { MutableInteractionSource() }
    MorphCard(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f),
        onClick = onClick,
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeTile(category = FileCategoryManager.getCategory(file.extension))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOf(formatFileSize(file.size), file.extension.uppercase())
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = strings.menuAction, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = LocalMorphColors.current.card
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.actionShare) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.btnDelete, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }
}

// --------------------------------------------------
// SECTION LABEL: one typographic voice for group titles
// --------------------------------------------------

@Composable
fun MorphSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --------------------------------------------------
// FILE SIZE FORMATTER
// --------------------------------------------------

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (
        log10(bytes.toDouble()) / log10(1024.0)
    ).toInt().coerceIn(0, units.lastIndex)

    val size = bytes / 1024.0.pow(digitGroups.toDouble())

    return DecimalFormat("#,##0.#").format(size) + " " + units[digitGroups]
}

// Transitional shim: Tools and Settings still draw their own header; removed when they move to MorphTopBar
@Composable
fun MorphScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
