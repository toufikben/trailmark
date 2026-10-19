package dev.toufik.trailmark.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val DeepSky = Color(0xFF08192B)
private val DeepTeal = Color(0xFF0E3A3F)
private val Snow = Color(0xFFE2F0F7)
private val RidgeFar = Color(0xFF1C3A4B)
private val RidgeNear = Color(0xFF27505F)
private val RouteGreen = Color(0xFFA3E635)
private val SunYellow = Color(0xFFFDE68A)

/**
 * Animated cold-start splash: two ridges under a low sun while a runner, a
 * cyclist and a climber move toward the mountain and a dashed route draws in.
 * Fades itself out and calls [onDone] once the intro has played.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val fade = remember { Animatable(1f) }
    val reveal = remember { Animatable(0f) }
    val pulse = rememberInfiniteTransition(label = "splash")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Restart),
        label = "stride",
    )

    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(1500))
        delay(900)
        fade.animateTo(0f, tween(450))
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Brush.verticalGradient(listOf(DeepSky, DeepTeal))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val sunR = w * 0.075f
            val sun = Offset(w * 0.20f, h * 0.24f)
            drawCircle(SunYellow.copy(alpha = 0.10f), sunR * 2.1f, sun)
            drawCircle(SunYellow.copy(alpha = 0.14f), sunR * 1.5f, sun)
            drawCircle(SunYellow.copy(alpha = 0.85f), sunR, sun)

            val baseY = h * 0.62f
            val farPeak = Offset(w * 0.38f, h * 0.24f)
            val far = Path().apply {
                moveTo(w * 0.03f, baseY)
                lineTo(farPeak.x, farPeak.y)
                lineTo(w * 0.74f, baseY)
                close()
            }
            drawPath(far, RidgeFar)
            drawPath(snowCap(farPeak, w * 0.055f, w * 0.035f), Snow.copy(alpha = 0.55f))

            val nearPeak = Offset(w * 0.72f, h * 0.30f)
            val near = Path().apply {
                moveTo(w * 0.40f, baseY)
                lineTo(nearPeak.x, nearPeak.y)
                lineTo(w * 1.04f, baseY)
                close()
            }
            drawPath(near, RidgeNear)
            drawPath(snowCap(nearPeak, w * 0.060f, w * 0.040f), Snow.copy(alpha = 0.70f))

            drawRoundRect(
                color = Color(0xFF0B2431).copy(alpha = 0.85f),
                topLeft = Offset(-w * 0.02f, baseY),
                size = Size(w * 1.04f, h * 0.028f),
                cornerRadius = CornerRadius(h * 0.014f),
            )

            val route = Path().apply {
                moveTo(w * 0.08f, h * 0.93f)
                cubicTo(w * 0.28f, h * 0.86f, w * 0.40f, h * 0.80f, w * 0.55f, h * 0.74f)
                cubicTo(w * 0.62f, h * 0.70f, w * 0.66f, h * 0.62f, w * 0.68f, h * 0.55f)
            }
            val measure = PathMeasure()
            measure.setPath(route, false)
            val done = measure.length * reveal.value
            val partial = Path()
            measure.getSegment(0f, done, partial, true)
            drawPath(
                partial,
                RouteGreen.copy(alpha = 0.95f),
                style = Stroke(width = w * 0.017f, cap = StrokeCap.Round),
            )
            drawCircle(RouteGreen, w * 0.020f, measure.getPosition(done))

            val walkBob = (sin(phase * 2f * PI.toFloat()) * h * 0.004f)
            drawRunner(
                center = Offset(w * 0.22f, h * 0.82f + walkBob),
                scale = h * 0.13f,
                phase = phase,
                color = Snow.copy(alpha = 0.92f),
            )
            drawCyclist(
                center = Offset(w * 0.47f, h * 0.83f),
                scale = h * 0.11f,
                phase = phase,
                color = Snow.copy(alpha = 0.90f),
            )
            drawClimber(
                center = Offset(w * 0.655f, h * 0.585f),
                scale = h * 0.105f,
                phase = phase,
                color = Snow.copy(alpha = 0.92f),
            )
        }

        Text(
            text = "Trailmark",
            color = Palette.text,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
                .graphicsLayer { alpha = reveal.value },
        )
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

private fun DrawScope.drawRunner(center: Offset, scale: Float, phase: Float, color: Color) {
    val sw = scale * 0.11f
    val swing = sin(phase * 2f * PI.toFloat())
    val hip = center
    val shoulder = Offset(hip.x, hip.y - scale * 0.55f)
    drawCircle(color, scale * 0.17f, Offset(hip.x, hip.y - scale * 0.80f))
    drawLine(color, hip, shoulder, sw, StrokeCap.Round)
    drawLine(color, shoulder, Offset(shoulder.x + scale * 0.42f * swing, shoulder.y + scale * 0.34f), sw, StrokeCap.Round)
    drawLine(color, shoulder, Offset(shoulder.x - scale * 0.42f * swing, shoulder.y + scale * 0.30f), sw, StrokeCap.Round)
    drawLine(color, hip, Offset(hip.x + scale * 0.50f * swing, hip.y + scale * 0.62f), sw, StrokeCap.Round)
    drawLine(color, hip, Offset(hip.x - scale * 0.50f * swing, hip.y + scale * 0.62f), sw, StrokeCap.Round)
}

private fun DrawScope.drawCyclist(center: Offset, scale: Float, phase: Float, color: Color) {
    val sw = scale * 0.085f
    val wheelR = scale * 0.30f
    val back = Offset(center.x - scale * 0.55f, center.y)
    val front = Offset(center.x + scale * 0.55f, center.y)
    val angle = phase * 2f * PI.toFloat()
    for (c in listOf(back, front)) {
        drawCircle(color, wheelR, c, style = Stroke(width = sw))
        for (k in 0 until 3) {
            val a = angle + k * PI.toFloat() / 3f
            val dx = cos(a) * wheelR
            val dy = sin(a) * wheelR
            drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), sw * 0.6f)
        }
    }
    val crank = Offset(center.x, center.y - wheelR * 0.25f)
    val seat = Offset(center.x - scale * 0.22f, center.y - scale * 0.52f)
    val bars = Offset(center.x + scale * 0.34f, center.y - scale * 0.50f)
    drawLine(color, back, seat, sw, StrokeCap.Round)
    drawLine(color, seat, crank, sw, StrokeCap.Round)
    drawLine(color, crank, front, sw, StrokeCap.Round)
    drawLine(color, seat, bars, sw, StrokeCap.Round)
    val shoulder = Offset(center.x + scale * 0.12f, center.y - scale * 0.82f)
    drawCircle(color, scale * 0.15f, Offset(shoulder.x + scale * 0.04f, shoulder.y - scale * 0.16f))
    drawLine(color, seat, shoulder, sw, StrokeCap.Round)
    drawLine(color, shoulder, bars, sw * 0.9f, StrokeCap.Round)
    val pedal = Offset(
        crank.x + cos(angle) * scale * 0.16f,
        crank.y + sin(angle) * scale * 0.16f,
    )
    drawLine(color, seat, pedal, sw, StrokeCap.Round)
}

private fun DrawScope.drawClimber(center: Offset, scale: Float, phase: Float, color: Color) {
    val sw = scale * 0.11f
    val reach = sin(phase * 2f * PI.toFloat())
    val hip = center
    val shoulder = Offset(hip.x + scale * 0.06f, hip.y - scale * 0.42f)
    drawCircle(color, scale * 0.16f, Offset(shoulder.x + scale * 0.02f, shoulder.y - scale * 0.18f))
    drawLine(color, hip, shoulder, sw, StrokeCap.Round)
    drawLine(
        color,
        shoulder,
        Offset(shoulder.x + scale * (0.30f + 0.14f * reach), shoulder.y - scale * 0.34f),
        sw,
        StrokeCap.Round,
    )
    drawLine(
        color,
        shoulder,
        Offset(shoulder.x + scale * (0.18f - 0.10f * reach), shoulder.y - scale * 0.26f),
        sw,
        StrokeCap.Round,
    )
    drawLine(color, hip, Offset(hip.x - scale * 0.34f, hip.y + scale * 0.40f), sw, StrokeCap.Round)
    drawLine(color, hip, Offset(hip.x - scale * 0.10f, hip.y + scale * 0.44f), sw, StrokeCap.Round)
}

private fun cos(x: Float): Float = kotlin.math.cos(x)
