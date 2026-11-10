package com.morph.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morph.app.ui.theme.MorphIndigo
import com.morph.app.ui.theme.MorphPink
import com.morph.app.ui.theme.MorphViolet

@Composable
fun MorphLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showContainer: Boolean = true
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Container Gradient
            if (showContainer) {
                val cornerRadius = CornerRadius(w * 0.28f, h * 0.28f)
                val roundRect = RoundRect(0f, 0f, w, h, cornerRadius)
                val bgPath = Path().apply { addRoundRect(roundRect) }

                drawPath(
                    path = bgPath,
                    brush = Brush.linearGradient(
                        colors = listOf(MorphIndigo, MorphViolet, MorphPink),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )

                // Subtle inner glossy highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.3f, h * 0.25f),
                        radius = w * 0.45f
                    )
                )
            }

            // 2. Geometric "M" Morph Symbol
            // Left stem, central transform valley, right stem with dynamic morph curves
            val strokeWidth = if (showContainer) w * 0.085f else w * 0.12f
            val logoColor = if (showContainer) Color.White else MorphIndigo

            val mPath = Path().apply {
                // Left leg base
                moveTo(w * 0.24f, h * 0.72f)
                // Left leg up
                lineTo(w * 0.24f, h * 0.32f)
                // Center valley down
                lineTo(w * 0.50f, h * 0.58f)
                // Center peak up
                lineTo(w * 0.76f, h * 0.32f)
                // Right leg down
                lineTo(w * 0.76f, h * 0.72f)
            }

            drawPath(
                path = mPath,
                color = logoColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Transformation Accent Node (Represents file metamorphosis)
            val dotRadius = strokeWidth * 0.55f
            drawCircle(
                color = if (showContainer) MorphPink.copy(alpha = 0.9f) else MorphPink,
                radius = dotRadius,
                center = Offset(w * 0.50f, h * 0.28f)
            )
            drawCircle(
                color = Color.White,
                radius = dotRadius * 0.6f,
                center = Offset(w * 0.50f, h * 0.28f)
            )
        }
    }
}
