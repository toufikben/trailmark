package dev.toufik.trailmark.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One place for the durations and easings every screen pulses with. */
object Motion {
    const val FAST = 180
    const val MED = 320
    const val SLOW = 640
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
}

/** Entrance: fades and slides up shortly after composition, staggered by [index]. */
@Composable
fun Modifier.appearIn(index: Int = 0, from: Dp = 14.dp): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(Motion.SLOW, delayMillis = index * 70, easing = Motion.EaseOut),
        label = "appear",
    )
    return graphicsLayer {
        alpha = anim
        translationY = (1f - anim) * from.toPx()
    }
}
