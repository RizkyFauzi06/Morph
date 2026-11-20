package com.morph.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morph.app.core.AppStrings
import com.morph.app.core.FileCategory
import com.morph.app.ui.components.FileTypeTile
import com.morph.app.ui.components.MorphCard
import com.morph.app.ui.components.MorphLogo
import com.morph.app.ui.components.pressScale
import com.morph.app.ui.theme.LocalReduceMotion
import com.morph.app.ui.theme.MorphSpace

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current

    // The logo block fades and settles in once; it never moves after that
    val intro = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    // The file tiles around the logo drift gently, read only in graphicsLayer (redraw, no relayout)
    val drift: () -> Float = if (reduceMotion) {
        { 0f }
    } else {
        val transition = rememberInfiniteTransition(label = "welcomeDrift")
        val value = transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "drift"
        )
        val read: () -> Float = { value.value }
        read
    }
    val ctaInteraction = remember { MutableInteractionSource() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = MorphSpace.xl, vertical = MorphSpace.l)
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressScale(ctaInteraction, 0.98f),
                    shape = MaterialTheme.shapes.medium,
                    interactionSource = ctaInteraction
                ) {
                    Text(text = strings.btnGetStarted, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(MorphSpace.s))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MorphSpace.xl, vertical = MorphSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo with the four file types it converts orbiting it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(248.dp)
                    .graphicsLayer {
                        alpha = intro.value
                        val s = 0.94f + 0.06f * intro.value
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(216.dp)
                        .border(1.dp, scheme.primary.copy(alpha = 0.14f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer.copy(alpha = 0.6f))
                )
                MorphLogo(size = 96.dp)

                OrbitTile(FileCategory.IMAGE, BiasAlignment(-0.82f, -0.72f)) { drift() }
                OrbitTile(FileCategory.VIDEO, BiasAlignment(0.84f, -0.42f)) { -drift() }
                OrbitTile(FileCategory.PDF, BiasAlignment(-0.68f, 0.86f)) { -drift() }
                OrbitTile(FileCategory.AUDIO, BiasAlignment(0.7f, 0.9f)) { drift() }
            }

            Spacer(Modifier.height(MorphSpace.l))
            Text(
                text = "Morph",
                style = MaterialTheme.typography.displayLarge,
                color = scheme.onBackground
            )
            Spacer(Modifier.height(MorphSpace.xs))
            Text(
                text = strings.welcomeHeadline,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(MorphSpace.xs))
            Text(
                text = strings.welcomeTagline,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(MorphSpace.xl))
            MorphCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(MorphSpace.screen),
                    verticalArrangement = Arrangement.spacedBy(MorphSpace.l)
                ) {
                    FeatureHighlightRow(
                        icon = Icons.Rounded.OfflineBolt,
                        title = strings.welcomeFeatureOfflineTitle,
                        desc = strings.welcomeFeatureOfflineDesc
                    )
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Sync,
                        title = strings.welcomeFeatureFastTitle,
                        desc = strings.welcomeFeatureFastDesc
                    )
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Lock,
                        title = strings.welcomeFeaturePrivacyTitle,
                        desc = strings.welcomeFeaturePrivacyDesc
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.OrbitTile(
    category: FileCategory,
    alignment: Alignment,
    drift: () -> Float
) {
    FileTypeTile(
        category = category,
        size = 52.dp,
        iconSize = 26.dp,
        cornerRadius = 15.dp,
        modifier = Modifier
            .align(alignment)
            .graphicsLayer { translationY = drift() * 6.dp.toPx() }
    )
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
