package com.morph.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionCapabilityManager
import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import com.morph.app.ui.components.FileTypeTile
import com.morph.app.ui.components.MorphCard
import com.morph.app.ui.components.MorphSectionLabel
import com.morph.app.ui.theme.MorphMotion
import com.morph.app.ui.theme.MorphSpace

/** One conversion tool; its From/To lists come from the engine, so they never drift from what the sheet offers. */
private data class ToolSpec(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: FileCategory,
    val from: List<String>,
    val to: List<String>
)

private fun toolRoute(sources: Collection<String>, keepTarget: (String) -> Boolean): Pair<List<String>, List<String>> {
    val from = mutableListOf<String>()
    val to = linkedSetOf<String>()
    sources.forEach { src ->
        val targets = ConversionCapabilityManager.getTargetFormats("file.$src").filter(keepTarget)
        if (targets.isNotEmpty()) {
            from += src
            to += targets
        }
    }
    return from to to.toList()
}

@Composable
fun ToolsScreen(
    onPickFileForTool: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current

    val tools = remember(strings) {
        fun spec(
            key: String,
            title: String,
            description: String,
            icon: ImageVector,
            category: FileCategory,
            sources: Collection<String>,
            keepTarget: (String) -> Boolean = { true }
        ): ToolSpec {
            val (from, to) = toolRoute(sources, keepTarget)
            return ToolSpec(key, title, description, icon, category, from, to)
        }
        val videoSources = FileCategoryManager.extensionsOf(FileCategory.VIDEO)
        val isAudio = { ext: String -> FileCategoryManager.getCategory(ext) == FileCategory.AUDIO }
        listOf(
            spec("video", strings.toolVideoTitle, strings.toolVideoDesc, Icons.Rounded.VideoFile, FileCategory.VIDEO, videoSources) { !isAudio(it) },
            spec("extract", strings.toolAudioTitle, strings.toolAudioDesc, Icons.Rounded.AudioFile, FileCategory.AUDIO, videoSources) { isAudio(it) },
            spec("audio", strings.toolAudioConvertTitle, strings.toolAudioConvertDesc, Icons.Rounded.GraphicEq, FileCategory.AUDIO, FileCategoryManager.extensionsOf(FileCategory.AUDIO)),
            spec("image", strings.toolImageTitle, strings.toolImageDesc, Icons.Rounded.Image, FileCategory.IMAGE, FileCategoryManager.extensionsOf(FileCategory.IMAGE) - "svg"),
            spec("svg", strings.toolSvgTitle, strings.toolSvgDesc, Icons.Rounded.Gesture, FileCategory.IMAGE, listOf("svg")),
            spec("pdf", strings.toolPdfTitle, strings.toolPdfDesc, Icons.Rounded.PictureAsPdf, FileCategory.PDF, listOf("pdf")),
            spec("epub", strings.toolEpubTitle, strings.toolEpubDesc, Icons.AutoMirrored.Rounded.MenuBook, FileCategory.EBOOK, listOf("epub", "txt"))
        ).filter { it.from.isNotEmpty() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MorphSpace.screen,
            end = MorphSpace.screen,
            top = MorphSpace.xs,
            bottom = MorphSpace.xl
        ),
        verticalArrangement = Arrangement.spacedBy(MorphSpace.m)
    ) {
        // The way most files arrive: featured first, as in the reference
        item(key = "share") {
            ShareFeatureCard(
                title = strings.toolShareTitle,
                description = strings.toolShareDesc,
                detail = strings.toolShareDetail
            )
        }

        item(key = "conversion_heading") {
            SectionHeading(
                title = strings.toolsConversionTitle,
                subtitle = strings.toolsConversionSubtitle,
                modifier = Modifier.padding(top = MorphSpace.m)
            )
        }

        items(tools, key = { it.key }) { tool ->
            ToolFeatureCard(tool)
        }

        item(key = "other_heading") {
            SectionHeading(
                title = strings.toolsOtherWaysTitle,
                subtitle = strings.toolsOtherWaysSubtitle,
                modifier = Modifier.padding(top = MorphSpace.m)
            )
        }

        item(key = "batch") {
            InfoFeatureCard(
                title = strings.toolBatchTitle,
                description = strings.toolBatchDesc,
                detail = strings.toolBatchDetail,
                icon = Icons.Rounded.DynamicFeed
            )
        }

        item(key = "offline") {
            InfoFeatureCard(
                title = strings.toolOfflineTitle,
                description = strings.toolOfflineDesc,
                detail = strings.toolOfflineDetail,
                icon = Icons.Rounded.WifiOff
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MorphSectionLabel(text = title)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShareFeatureCard(
    title: String,
    description: String,
    detail: String
) {
    val scheme = MaterialTheme.colorScheme
    MorphCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(horizontal = MorphSpace.screen, vertical = MorphSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(MorphSpace.l))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(MorphSpace.l))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceContainer)
                    .padding(MorphSpace.m)
            )
        }
    }
}

@Composable
private fun ToolFeatureCard(tool: ToolSpec) {
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme
    MorphCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MorphSpace.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeTile(category = tool.category, icon = tool.icon, size = 40.dp, iconSize = 22.dp)
                Spacer(Modifier.width(MorphSpace.m))
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MorphSpace.m)
            )
            Column(
                modifier = Modifier
                    .padding(top = MorphSpace.m)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = MorphSpace.m, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FormatLine(label = strings.formatsFromLabel, formats = tool.from)
                FormatLine(label = strings.formatsToLabel, formats = tool.to)
            }
        }
    }
}

@Composable
private fun FormatLine(label: String, formats: List<String>) {
    val joined = formats.joinToString(" · ") { it.uppercase() }
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = joined,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Short fact with an optional longer explanation behind a tap; the chevron turns, the detail unfolds. */
@Composable
private fun InfoFeatureCard(
    title: String,
    description: String,
    detail: String,
    icon: ImageVector
) {
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MorphMotion.medium(),
        label = "chevron"
    )

    MorphCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        borderColor = if (expanded) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant
    ) {
        Column(modifier = Modifier.padding(MorphSpace.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(MorphSpace.m))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) strings.toolCollapse else strings.toolExpand,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MorphSpace.m)
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(MorphMotion.medium()) + expandVertically(MorphMotion.medium()),
                exit = fadeOut(MorphMotion.fast()) + shrinkVertically(MorphMotion.medium())
            ) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = MorphSpace.s)
                )
            }
        }
    }
}
