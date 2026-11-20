package com.morph.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

// ============================================================
// SPACING (spec 4.1, multiples of 4 dp)
// ============================================================
object MorphSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screen = 20.dp   // default horizontal screen padding
    val xl = 24.dp
    val xxl = 32.dp
}

// ============================================================
// MOTION (spec 13): short, non-bouncy, the same few curves everywhere
// ============================================================
object MorphMotion {
    const val FAST = 180      // selection color/border changes
    const val MEDIUM = 240    // fades, list item insertion
    const val SLOW = 320      // screen transitions, progress bar

    fun <T> fast() = tween<T>(FAST, easing = FastOutSlowInEasing)
    fun <T> medium() = tween<T>(MEDIUM, easing = FastOutSlowInEasing)
    fun <T> slow() = tween<T>(SLOW, easing = FastOutSlowInEasing)

    /** Size changes (expanding cards, sheet sections): settle without overshoot. */
    val sizeSpring = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
