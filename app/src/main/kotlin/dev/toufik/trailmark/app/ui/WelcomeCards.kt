package dev.toufik.trailmark.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.extraText
import kotlinx.coroutines.launch

/**
 * First-run tour: six full-bleed cards, one idea each, drawn procedurally so the
 * screen costs no assets and matches the map's dark athletic palette. The last
 * card is the honest one — a single developer asking to be paid.
 */
private const val WELCOME_PAGES = 6

private val Ink = Color(0xFF0B0F11)
private val NightA = Color(0xFF0A1A22)
private val NightB = Color(0xFF123039)
private val DuskA = Color(0xFF131A22)
private val DuskB = Color(0xFF23303A)
private val OnGold = Color(0xFF241A00)
private val SoftWhite = Color(0xFFE9F1EC)

/** Keys per page, resolved through [extraText] so all 16 locales share the layout. */
private fun titleKey(page: Int) = "w${page + 1}Title"
private fun bodyKey(page: Int) = "w${page + 1}Body"

@Composable
fun WelcomeScreen(onDone: () -> Unit, onBuy: () -> Unit) {
    val lang = LocalLang.current
    val pager = rememberPagerState(pageCount = { WELCOME_PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == WELCOME_PAGES - 1

    // Back steps one card out before it can leave the app, so a stray press on the
    // tour never dumps the user onto the home screen mid-read.
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                if (!last) {
                    TextButton(onClick = onDone) {
                        Text(
                            text = extraText(lang, "welcomeSkip"),
                            color = Palette.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                pageSpacing = 16.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            ) { page ->
                val settle by animateFloatAsState(
                    targetValue = if (pager.currentPage == page) 1f else 0.86f,
                    animationSpec = tween(Motion.MED, easing = Motion.EaseOut),
                    label = "card-scale",
                )
                CardPage(
                    page = page,
                    lang = lang,
                    modifier = Modifier.graphicsLayer {
                        val fraction = (settle - 0.86f) / 0.14f
                        alpha = 0.55f + 0.45f * fraction
                        scaleX = settle
                        scaleY = settle
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(WELCOME_PAGES) { i ->
                    val active = pager.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (active) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) Palette.accent else Palette.textSecondary.copy(alpha = 0.32f),
                            ),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (last) {
                    Button(
                        onClick = onBuy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.gold,
                            contentColor = OnGold,
                        ),
                    ) {
                        Text(
                            text = extraText(lang, "w6Cta"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = extraText(lang, "welcomeSkip"),
                            color = Palette.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    Button(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.accent,
                            contentColor = Palette.onAccent,
                        ),
                    ) {
                        Text(
                            text = extraText(lang, "welcomeNext"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPage(page: Int, lang: dev.toufik.trailmark.app.Lang, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Short phones and large system font sizes both push the body below the
            // fold; scrolling keeps the whole card reachable instead of clipping it.
            .verticalScroll(rememberScrollState()),
    ) {
        SceneArt(
            page = page,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.06f)
                .clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = extraText(lang, titleKey(page)),
            color = if (page == WELCOME_PAGES - 1) Palette.gold else Palette.text,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = extraText(lang, bodyKey(page)),
            color = Palette.textSecondary,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- artwork

@Composable
private fun SceneArt(page: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = 22.dp.toPx()
        val frame = Path().apply {
            addRoundRect(
                RoundRect(0f, 0f, w, h, CornerRadius(radius, radius)),
            )
        }
        clipPath(frame) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = when {
                        page == 5 -> listOf(Color(0xFF1B1408), Color(0xFF0D0A05))
                        page % 2 == 0 -> listOf(NightA, NightB)
                        else -> listOf(DuskA, DuskB)
                    },
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (page == 5) Palette.gold else Palette.accent).copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.5f, h * 0.34f),
                    radius = w * 0.72f,
                ),
                radius = w * 0.72f,
                center = Offset(w * 0.5f, h * 0.34f),
            )
            when (page) {
                0 -> bikeScene(w, h)
                1 -> runScene(w, h)
                2 -> hikeScene(w, h)
                3 -> planScene(w, h)
                4 -> weatherScene(w, h)
                else -> supportScene(w, h)
            }
        }
    }
}

/** Shared ground band, so every card sits on the same horizon. */
private fun DrawScope.ground(w: Float, h: Float, tone: Color = Color(0xFF0A1216)) {
    val y = h * 0.80f
    drawLine(
        color = tone,
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = h * 0.055f,
    )
    drawLine(
        color = SoftWhite.copy(alpha = 0.07f),
        start = Offset(0f, y - h * 0.024f),
        end = Offset(w, y - h * 0.024f),
        strokeWidth = 1.5f,
    )
}

private fun DrawScope.ridge(w: Float, h: Float, peaks: List<Offset>, tone: Color) {
    val base = h * 0.80f
    val p = Path().apply {
        moveTo(-w * 0.05f, base)
        peaks.forEach { lineTo(it.x, it.y) }
        lineTo(w * 1.05f, base)
        close()
    }
    drawPath(p, tone)
}

/** A wheel: rim ring plus a light hub. */
private fun DrawScope.wheel(cx: Float, cy: Float, r: Float, tone: Color) {
    drawCircle(tone, r, Offset(cx, cy), style = Stroke(width = r * 0.20f))
    drawCircle(tone.copy(alpha = 0.55f), r * 0.16f, Offset(cx, cy))
}

private fun DrawScope.stickFigure(
    cx: Float,
    baseY: Float,
    scale: Float,
    tone: Color,
    lean: Float = 0f,
) {
    val headR = scale * 0.115f
    val headY = baseY - scale * 0.90f
    val hipY = baseY - scale * 0.46f
    val shoulderY = baseY - scale * 0.72f
    val stroke = scale * 0.085f

    drawCircle(tone, headR, Offset(cx + lean * scale * 0.10f, headY))
    drawLine(
        tone,
        Offset(cx + lean * scale * 0.05f, shoulderY),
        Offset(cx, hipY),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(tone, Offset(cx, shoulderY), Offset(cx - scale * 0.20f, baseY - scale * 0.56f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(tone, Offset(cx, shoulderY), Offset(cx + scale * 0.20f, baseY - scale * 0.56f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(tone, Offset(cx, hipY), Offset(cx - scale * 0.19f, baseY), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(tone, Offset(cx, hipY), Offset(cx + scale * 0.19f, baseY - scale * 0.04f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.bikeScene(w: Float, h: Float) {
    ground(w, h)
    ridge(
        w, h,
        listOf(
            Offset(w * 0.06f, h * 0.72f),
            Offset(w * 0.24f, h * 0.55f),
            Offset(w * 0.42f, h * 0.70f),
            Offset(w * 0.60f, h * 0.52f),
            Offset(w * 0.80f, h * 0.68f),
            Offset(w * 0.96f, h * 0.60f),
        ),
        Color(0xFF16333C),
    )
    val wheelR = w * 0.098f
    val leftX = w * 0.33f
    val rightX = w * 0.67f
    val wheelY = h * 0.74f
    val ink = Palette.accent
    wheel(leftX, wheelY, wheelR, ink)
    wheel(rightX, wheelY, wheelR, ink)
    // Frame.
    val seat = Offset(w * 0.40f, h * 0.545f)
    val crank = Offset(w * 0.50f, h * 0.735f)
    val bar = Offset(w * 0.585f, h * 0.545f)
    val frameStroke = w * 0.017f
    drawLine(ink, seat, crank, strokeWidth = frameStroke, cap = StrokeCap.Round)
    drawLine(ink, crank, bar, strokeWidth = frameStroke, cap = StrokeCap.Round)
    drawLine(ink, seat, bar, strokeWidth = frameStroke, cap = StrokeCap.Round)
    drawLine(ink, seat, Offset(leftX, wheelY), strokeWidth = frameStroke, cap = StrokeCap.Round)
    drawLine(ink, bar, Offset(rightX, wheelY), strokeWidth = frameStroke, cap = StrokeCap.Round)
    drawCircle(ink, w * 0.012f, crank)
    // Rider.
    stickFigure(w * 0.505f, h * 0.60f, w * 0.20f, SoftWhite, lean = 1f)
    // Speed streaks.
    repeat(3) { i ->
        val y = h * (0.50f + i * 0.055f)
        drawLine(
            color = Palette.accent.copy(alpha = 0.45f - i * 0.10f),
            start = Offset(w * (0.06f + i * 0.02f), y),
            end = Offset(w * (0.20f + i * 0.03f), y),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.runScene(w: Float, h: Float) {
    ground(w, h)
    ridge(
        w, h,
        listOf(
            Offset(w * 0.00f, h * 0.66f),
            Offset(w * 0.20f, h * 0.50f),
            Offset(w * 0.38f, h * 0.66f),
            Offset(w * 0.58f, h * 0.44f),
            Offset(w * 0.78f, h * 0.64f),
            Offset(w * 1.00f, h * 0.55f),
        ),
        Color(0xFF1A2B33),
    )
    // Route line under the runner.
    val route = Path().apply {
        moveTo(w * 0.02f, h * 0.80f)
        cubicTo(w * 0.24f, h * 0.76f, w * 0.34f, h * 0.70f, w * 0.56f, h * 0.71f)
        cubicTo(w * 0.74f, h * 0.72f, w * 0.86f, h * 0.78f, w * 0.98f, h * 0.78f)
    }
    drawPath(
        route,
        Palette.accent.copy(alpha = 0.22f),
        style = Stroke(width = w * 0.045f, cap = StrokeCap.Round),
    )
    drawPath(
        route,
        Palette.accent,
        style = Stroke(width = w * 0.012f, cap = StrokeCap.Round),
    )
    stickFigure(w * 0.52f, h * 0.735f, w * 0.30f, SoftWhite, lean = 1.4f)
    // Motion arcs behind.
    repeat(2) { i ->
        val r = w * (0.20f + i * 0.055f)
        drawArc(
            color = Palette.accent.copy(alpha = 0.30f - i * 0.10f),
            startAngle = 130f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(w * 0.30f - r, h * 0.56f - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
    // Distance marker.
    drawCircle(Palette.accent, w * 0.016f, Offset(w * 0.80f, h * 0.717f))
    drawCircle(SoftWhite, w * 0.006f, Offset(w * 0.80f, h * 0.717f))
}

private fun DrawScope.hikeScene(w: Float, h: Float) {
    ground(w, h)
    // Big peaks with snow caps.
    ridge(
        w, h,
        listOf(
            Offset(w * 0.00f, h * 0.78f),
            Offset(w * 0.22f, h * 0.30f),
            Offset(w * 0.46f, h * 0.78f),
        ),
        Color(0xFF16323B),
    )
    ridge(
        w, h,
        listOf(
            Offset(w * 0.38f, h * 0.78f),
            Offset(w * 0.66f, h * 0.36f),
            Offset(w * 0.98f, h * 0.78f),
        ),
        Color(0xFF1D3B45),
    )
    val capA = Path().apply {
        moveTo(w * 0.22f, h * 0.30f)
        lineTo(w * 0.15f, h * 0.44f)
        lineTo(w * 0.20f, h * 0.40f)
        lineTo(w * 0.22f, h * 0.45f)
        lineTo(w * 0.25f, h * 0.40f)
        lineTo(w * 0.29f, h * 0.44f)
        close()
    }
    drawPath(capA, SoftWhite.copy(alpha = 0.55f))
    val capB = Path().apply {
        moveTo(w * 0.66f, h * 0.36f)
        lineTo(w * 0.58f, h * 0.50f)
        lineTo(w * 0.63f, h * 0.46f)
        lineTo(w * 0.66f, h * 0.51f)
        lineTo(w * 0.69f, h * 0.46f)
        lineTo(w * 0.74f, h * 0.50f)
        close()
    }
    drawPath(capB, SoftWhite.copy(alpha = 0.62f))
    // Trail up the slope.
    val trail = Path().apply {
        moveTo(w * 0.10f, h * 0.795f)
        cubicTo(w * 0.30f, h * 0.74f, w * 0.40f, h * 0.70f, w * 0.52f, h * 0.66f)
    }
    drawPath(
        trail,
        Palette.accent,
        style = Stroke(
            width = w * 0.010f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.030f, w * 0.022f)),
        ),
    )
    // Hiker with backpack and pole.
    val cx = w * 0.60f
    stickFigure(cx, h * 0.80f, w * 0.26f, SoftWhite, lean = 0.4f)
    drawRoundRect(
        color = Palette.accent,
        topLeft = Offset(cx + w * 0.035f, h * 0.545f),
        size = Size(w * 0.052f, h * 0.085f),
        cornerRadius = CornerRadius(w * 0.014f),
    )
    drawLine(
        color = SoftWhite,
        start = Offset(cx + w * 0.055f, h * 0.615f),
        end = Offset(cx + w * 0.115f, h * 0.795f),
        strokeWidth = w * 0.008f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.planScene(w: Float, h: Float) {
    ground(w, h)
    // Map grid.
    val gridTone = SoftWhite.copy(alpha = 0.06f)
    repeat(6) { i ->
        val x = w * (0.10f + i * 0.16f)
        drawLine(gridTone, Offset(x, 0f), Offset(x, h * 0.80f), strokeWidth = 1.4f)
    }
    repeat(5) { i ->
        val y = h * (0.14f + i * 0.14f)
        drawLine(gridTone, Offset(0f, y), Offset(w, y), strokeWidth = 1.4f)
    }
    // Planned line: dashed, because it is not walked yet.
    val planned = Path().apply {
        moveTo(w * 0.14f, h * 0.70f)
        lineTo(w * 0.32f, h * 0.52f)
        lineTo(w * 0.48f, h * 0.60f)
        lineTo(w * 0.66f, h * 0.36f)
        lineTo(w * 0.86f, h * 0.46f)
    }
    drawPath(
        planned,
        Palette.accent,
        style = Stroke(
            width = w * 0.014f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.034f, w * 0.024f)),
        ),
    )
    // Waypoints.
    listOf(
        Offset(w * 0.14f, h * 0.70f),
        Offset(w * 0.48f, h * 0.60f),
        Offset(w * 0.86f, h * 0.46f),
    ).forEach { p ->
        drawCircle(Palette.accent.copy(alpha = 0.22f), w * 0.036f, p)
        drawCircle(Palette.accent, w * 0.017f, p)
        drawCircle(Ink, w * 0.007f, p)
    }
    // Destination pin.
    val pin = Offset(w * 0.66f, h * 0.36f)
    val pinPath = Path().apply {
        moveTo(pin.x, pin.y + h * 0.075f)
        cubicTo(pin.x - w * 0.055f, pin.y + h * 0.012f, pin.x - w * 0.048f, pin.y - h * 0.055f, pin.x, pin.y - h * 0.055f)
        cubicTo(pin.x + w * 0.048f, pin.y - h * 0.055f, pin.x + w * 0.055f, pin.y + h * 0.012f, pin.x, pin.y + h * 0.075f)
        close()
    }
    drawPath(pinPath, SoftWhite)
    drawCircle(Ink, w * 0.016f, Offset(pin.x, pin.y - h * 0.012f))
}

private fun DrawScope.weatherScene(w: Float, h: Float) {
    ground(w, h)
    // Sun.
    val sun = Offset(w * 0.70f, h * 0.30f)
    drawCircle(Palette.gold.copy(alpha = 0.16f), w * 0.135f, sun)
    drawCircle(Palette.gold, w * 0.082f, sun)
    repeat(8) { i ->
        val a = (i * 45f) * (Math.PI / 180f).toFloat()
        val r1 = w * 0.115f
        val r2 = w * 0.150f
        drawLine(
            color = Palette.gold.copy(alpha = 0.65f),
            start = Offset(sun.x + cos(a) * r1, sun.y + sin(a) * r1),
            end = Offset(sun.x + cos(a) * r2, sun.y + sin(a) * r2),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round,
        )
    }
    // Cloud.
    val cloudTone = SoftWhite.copy(alpha = 0.85f)
    drawCircle(cloudTone, w * 0.070f, Offset(w * 0.27f, h * 0.34f))
    drawCircle(cloudTone, w * 0.052f, Offset(w * 0.35f, h * 0.375f))
    drawRoundRect(
        color = cloudTone,
        topLeft = Offset(w * 0.225f, h * 0.375f),
        size = Size(w * 0.185f, h * 0.052f),
        cornerRadius = CornerRadius(h * 0.026f),
    )
    // Best-window arc: the recommended hours lit in accent.
    val arcR = w * 0.235f
    val arcCenter = Offset(w * 0.40f, h * 0.735f)
    drawArc(
        color = SoftWhite.copy(alpha = 0.12f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(arcCenter.x - arcR, arcCenter.y - arcR),
        size = Size(arcR * 2, arcR * 2),
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round),
    )
    drawArc(
        color = Palette.accent,
        startAngle = 258f,
        sweepAngle = 52f,
        useCenter = false,
        topLeft = Offset(arcCenter.x - arcR, arcCenter.y - arcR),
        size = Size(arcR * 2, arcR * 2),
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round),
    )
    // Hand pointing at the good hour.
    val tip = Offset(
        arcCenter.x + cos(282f * (Math.PI / 180f).toFloat()) * arcR,
        arcCenter.y + sin(282f * (Math.PI / 180f).toFloat()) * arcR,
    )
    drawCircle(Palette.accent.copy(alpha = 0.25f), w * 0.038f, tip)
    drawCircle(Palette.accent, w * 0.016f, tip)
}

private fun DrawScope.supportScene(w: Float, h: Float) {
    ground(w, h, Color(0xFF120D05))
    // A single figure, alone — the developer.
    stickFigure(w * 0.5f, h * 0.80f, w * 0.30f, SoftWhite.copy(alpha = 0.88f), lean = 0f)
    // Gold ring around the badge: one person, not a team.
    val badge = Offset(w * 0.50f, h * 0.34f)
    drawCircle(Palette.gold.copy(alpha = 0.10f), w * 0.185f, badge)
    drawCircle(Palette.gold, w * 0.132f, badge, style = Stroke(width = w * 0.014f))
    // Heart in the badge.
    val hs = w * 0.070f
    val heart = Path().apply {
        moveTo(badge.x, badge.y + hs * 0.62f)
        cubicTo(badge.x - hs * 1.05f, badge.y - hs * 0.10f, badge.x - hs * 0.58f, badge.y - hs * 0.86f, badge.x, badge.y - hs * 0.28f)
        cubicTo(badge.x + hs * 0.58f, badge.y - hs * 0.86f, badge.x + hs * 1.05f, badge.y - hs * 0.10f, badge.x, badge.y + hs * 0.62f)
        close()
    }
    drawPath(heart, Palette.gold)
    // Sparkles: the "free updates" idea.
    listOf(
        Offset(w * 0.20f, h * 0.24f) to w * 0.020f,
        Offset(w * 0.80f, h * 0.30f) to w * 0.026f,
        Offset(w * 0.15f, h * 0.46f) to w * 0.014f,
        Offset(w * 0.86f, h * 0.50f) to w * 0.017f,
    ).forEach { (p, s) ->
        val spark = Path().apply {
            moveTo(p.x, p.y - s)
            lineTo(p.x + s * 0.28f, p.y - s * 0.28f)
            lineTo(p.x + s, p.y)
            lineTo(p.x + s * 0.28f, p.y + s * 0.28f)
            lineTo(p.x, p.y + s)
            lineTo(p.x - s * 0.28f, p.y + s * 0.28f)
            lineTo(p.x - s, p.y)
            lineTo(p.x - s * 0.28f, p.y - s * 0.28f)
            close()
        }
        drawPath(spark, Palette.gold.copy(alpha = 0.55f))
    }
    // No-ads, no-tracking tick row.
    val tickY = h * 0.86f
    repeat(3) { i ->
        val cx = w * (0.30f + i * 0.20f)
        drawCircle(Palette.gold.copy(alpha = 0.18f), w * 0.030f, Offset(cx, tickY))
        val tick = Path().apply {
            moveTo(cx - w * 0.014f, tickY)
            lineTo(cx - w * 0.004f, tickY + w * 0.010f)
            lineTo(cx + w * 0.014f, tickY - w * 0.012f)
        }
        drawPath(
            tick,
            Palette.gold,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}

private fun cos(a: Float) = kotlin.math.cos(a)
private fun sin(a: Float) = kotlin.math.sin(a)
