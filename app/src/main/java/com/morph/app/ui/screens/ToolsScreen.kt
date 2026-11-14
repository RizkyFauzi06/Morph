
package com.morph.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morph.app.core.AppStrings
import com.morph.app.ui.components.MorphScreenHeader

@Composable
fun ToolsScreen(
    onPickFileForTool: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(
            top = 20.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header — sesuai spec: "Tools" + deskripsi singkat
        item {
            MorphScreenHeader(
                title = strings.toolsTitle,
                subtitle = strings.toolsSubtitle
            )
        }

        // Conversion Tools Section
        item {
            SectionHeading(
                title = strings.toolsConversionTitle,
                subtitle = strings.toolsConversionSubtitle
            )
        }

        item {
            ToolFeatureCard(
                title = strings.toolVideoTitle,
                description = strings.toolVideoDesc,
                supportedFormats = "MKV · MP4 · MOV  →  MP4 · MOV · MKV",
                icon = Icons.Default.VideoFile
            )
        }

        item {
            ToolFeatureCard(
                title = strings.toolPdfTitle,
                description = strings.toolPdfDesc,
                supportedFormats = "PDF · JPG · PNG · WEBP  →  PDF · PNG · JPG",
                icon = Icons.Default.PictureAsPdf
            )
        }

        item {
            ToolFeatureCard(
                title = strings.toolAudioTitle,
                description = strings.toolAudioDesc,
                supportedFormats = "MP4 · MKV · MOV · WEBM  →  MP3 · M4A · WAV · FLAC · OGG · OPUS",
                icon = Icons.Default.Audiotrack
            )
        }

        item {
            ToolFeatureCard(
                title = strings.toolSvgTitle,
                description = strings.toolSvgDesc,
                supportedFormats = "SVG  →  PNG · JPG · WEBP",
                icon = Icons.Default.AutoAwesome
            )
        }

        item {
            ToolFeatureCard(
                title = strings.toolEpubTitle,
                description = strings.toolEpubDesc,
                supportedFormats = "EPUB  →  TXT",
                icon = Icons.Default.Book
            )
        }

        item {
            ToolFeatureCard(
                title = strings.toolImageTitle,
                description = strings.toolImageDesc,
                supportedFormats = "PNG · JPG · WEBP · BMP  →  PNG · JPG · WEBP · BMP · PDF",
                icon = Icons.Default.Image
            )
        }

        // Share & Usage Section
        item {
            SectionHeading(
                title = strings.toolsOtherWaysTitle,
                subtitle = strings.toolsOtherWaysSubtitle
            )
        }

        item {
            InfoFeatureCard(
                title = strings.toolShareTitle,
                description = strings.toolShareDesc,
                detail = strings.toolShareDetail,
                icon = Icons.Default.Share
            )
        }

        item {
            InfoFeatureCard(
                title = strings.toolBatchTitle,
                description = strings.toolBatchDesc,
                detail = strings.toolBatchDetail,
                icon = Icons.Default.DynamicFeed
            )
        }

        item {
            InfoFeatureCard(
                title = strings.toolOfflineTitle,
                description = strings.toolOfflineDesc,
                detail = strings.toolOfflineDetail,
                icon = Icons.Default.WifiOff
            )
        }

        item {
            InfoFeatureCard(
                title = strings.toolFormatTitle,
                description = strings.toolFormatDesc,
                detail = strings.toolFormatDetail,
                icon = Icons.Default.Extension
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolFeatureCard(
    title: String,
    description: String,
    supportedFormats: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FormatLabel(supportedFormats)
        }
    }
}

@Composable
private fun InfoFeatureCard(
    title: String,
    description: String,
    detail: String,
    icon: ImageVector
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) AppStrings.current.toolCollapse else AppStrings.current.toolExpand,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FeatureIcon(
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FormatLabel(
    formats: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = formats,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 7.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

