package dev.toufik.trailmark.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DeepSky = Color(0xFF06121F)
private val DeepTeal = Color(0xFF0B2A31)
private val Snow = Color(0xFFE2F0F7)
private val RidgeFar = Color(0xFF17313F)
private val RidgeNear = Color(0xFF204653)

/**
 * Cold-start splash: layered ridges, a single route line that draws up to the
 * summit, then the wordmark. Skips on tap, honours the system animation scale,
 * and hands off to the app within 1.6 seconds.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val motion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        scope.launch {
            fade.animateTo(0f, tween(if (motion) 300 else 120, easing = LinearEasing))
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        if (motion) reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(if (motion) 350 else 900)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Brush.verticalGradient(listOf(DeepSky, DeepTeal)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { finish() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val progress = reveal.value
            val baseY = h * 0.64f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(w * 0.58f, h * 0.30f),
                    radius = w * 0.55f,
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.58f, h * 0.30f),
            )

            val farPeak = Offset(w * 0.32f, h * 0.30f)
            val far = Path().apply {
                moveTo(-w * 0.02f, baseY)
                lineTo(farPeak.x, farPeak.y)
                lineTo(w * 0.74f, baseY)
                close()
            }
            drawPath(
                far,
                Brush.verticalGradient(
                    colors = listOf(RidgeFar, DeepTeal),
                    startY = farPeak.y,
                    endY = baseY,
                ),
            )
            drawPath(snowCap(farPeak, w * 0.052f, w * 0.034f), Snow.copy(alpha = 0.50f))

            val nearPeak = Offset(w * 0.68f, h * 0.355f)
            val near = Path().apply {
                moveTo(w * 0.36f, baseY)
                lineTo(nearPeak.x, nearPeak.y)
                lineTo(w * 1.02f, baseY)
                close()
            }
            drawPath(
                near,
                Brush.verticalGradient(
                    colors = listOf(RidgeNear, DeepSky),
                    startY = nearPeak.y,
                    endY = baseY,
                ),
            )
            drawPath(snowCap(nearPeak, w * 0.058f, w * 0.038f), Snow.copy(alpha = 0.62f))

            drawRoundRect(
                color = Color(0xFF081D27).copy(alpha = 0.9f),
                topLeft = Offset(-w * 0.02f, baseY),
                size = Size(w * 1.04f, h * 0.03f),
                cornerRadius = CornerRadius(h * 0.015f),
            )

            val route = Path().apply {
                moveTo(w * 0.12f, h * 0.90f)
                cubicTo(w * 0.30f, h * 0.83f, w * 0.32f, h * 0.66f, w * 0.47f, h * 0.585f)
                cubicTo(w * 0.58f, h * 0.525f, w * 0.60f, h * 0.44f, w * 0.665f, h * 0.385f)
            }
            val measure = PathMeasure()
            measure.setPath(route, false)
            val drawn = measure.length * progress
            val partial = Path()
            if (progress > 0.001f) {
                measure.getSegment(0f, drawn, partial, true)
                drawPath(
                    partial,
                    Palette.accent.copy(alpha = 0.16f),
                    style = Stroke(width = w * 0.055f, cap = StrokeCap.Round),
                )
                drawPath(
                    partial,
                    Palette.accent,
                    style = Stroke(width = w * 0.012f, cap = StrokeCap.Round),
                )
            }
            if (progress > 0.99f) {
                val end = measure.getPosition(measure.length)
                drawCircle(Palette.accent.copy(alpha = 0.22f), w * 0.038f, end)
                drawCircle(Palette.accent, w * 0.017f, end)
                drawCircle(Snow, w * 0.006f, end)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 56.dp)
                .graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 20.dp.toPx()
                },
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(2.dp)
                    .background(Palette.accent, RoundedCornerShape(1.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Trailmark",
                color = Palette.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 5.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "TRACK · EXPLORE · RETURN",
                color = Palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun snowCap(peak: Offset, halfWidth: Float, drop: Float): Path = Path().apply {
    moveTo(peak.x, peak.y)
    lineTo(peak.x - halfWidth, peak.y + drop)
    lineTo(peak.x - halfWidth * 0.35f, peak.y + drop * 0.72f)
    lineTo(peak.x, peak.y + drop * 1.05f)
    lineTo(peak.x + halfWidth * 0.35f, peak.y + drop * 0.78f)
    lineTo(peak.x + halfWidth, peak.y + drop)
    close()
}
