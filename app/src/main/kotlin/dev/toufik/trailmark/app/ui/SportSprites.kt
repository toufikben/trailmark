package dev.toufik.trailmark.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import dev.toufik.trailmark.engine.Sport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Frames per movement cycle for every sport sprite. */
const val SPRITE_FRAMES = 8

fun spriteImageId(sport: Sport, frame: Int): String = "me-${sport.name}-$frame"

private const val ACCENT = 0xFFC6FF3D.toInt()
private const val ACCENT_DIM = 0x8CC6FF3D.toInt()
private const val GEAR = 0xFF9BD32A.toInt()

/** Draws a 58 dp animated marker for the given sport and cycle frame. */
fun spriteBitmap(context: Context, sport: Sport, frame: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val s = (58f * d).roundToInt().coerceAtLeast(140)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Ground contact: a soft accent pool under the figure, no ring around it.
    p.color = 0x40C6FF3D
    c.drawOval(s * 0.20f, s * 0.770f, s * 0.80f, s * 0.890f, p)
    p.color = 0x59000000
    c.drawOval(s * 0.26f, s * 0.790f, s * 0.74f, s * 0.870f, p)

    // Speed streaks trail behind the figure while moving; the idle frame (0) stays clean.
    if (frame != 0) {
        val t = frame / SPRITE_FRAMES.toFloat()
        val wave = 0.35f + 0.65f * sin(t * PI).toFloat()
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0x73C6FF3D
            strokeWidth = s * 0.016f
        }
        c.drawLine(s * 0.075f, s * 0.400f, s * (0.075f + 0.13f * wave), s * 0.400f, sp)
        c.drawLine(s * 0.030f, s * 0.550f, s * (0.030f + 0.20f * wave), s * 0.550f, sp)
        c.drawLine(s * 0.095f, s * 0.680f, s * (0.095f + 0.11f * wave), s * 0.680f, sp)
    }

    // The figure itself, drawn once into a layer and composited with a soft dark halo
    // so it stays readable on both light and dark map styles.
    val fig = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val fc = Canvas(fig)
    fc.save()
    val k = s * 0.96f
    fc.translate((s - k) / 2f, (s - k) / 2f + s * 0.01f)
    fc.scale(k, k)
    when (sport) {
        Sport.BIKE -> drawCyclist(fc, frame)
        Sport.RUN -> drawRunner(fc, frame, swing = 0.62f, lean = 0.10f, bob = 0.013f, armSwing = 0.95f)
        Sport.WALK -> drawRunner(fc, frame, swing = 0.34f, lean = 0.0f, bob = 0.006f, armSwing = 0.55f)
        Sport.HIKE -> drawHiker(fc, frame)
    }
    fc.restore()
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(s * 0.045f, 0f, 0f, 0xE60E1113.toInt())
    }
    c.drawBitmap(fig, 0f, 0f, halo)
    return bmp
}

private fun stroke(x: Float, from: Float = 0f, to: Float = 1f): Float = x * (to - from) + from

private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }
    c.drawLine(x1, y1, x2, y2, p)
}

private fun chain(c: Canvas, xs: FloatArray, ys: FloatArray, w: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    for (i in 1 until xs.size) c.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], p)
}

private fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    c.drawCircle(x, y, r, p)
}

private fun disc(c: Canvas, x: Float, y: Float, r: Float, w: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        this.color = color
    }
    c.drawCircle(x, y, r, p)
}

/** Knee position for hip-[l1]-knee-[l2]-foot, knee pushed forward (+x) when possible. */
private fun kneeOf(
    hx: Float, hy: Float, fx: Float, fy: Float, l1: Float, l2: Float,
): Pair<Float, Float> {
    val dx = fx - hx
    val dy = fy - hy
    val d = sqrt(dx * dx + dy * dy).coerceIn(1e-4f, l1 + l2 - 1e-4f)
    val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
    val h = sqrt((l1 * l1 - a * a).coerceAtLeast(0f))
    val ux = dx / d
    val uy = dy / d
    val px = hx + ux * a
    val py = hy + uy * a
    val k1x = px + uy * h
    val k1y = py - ux * h
    val k2x = px - uy * h
    val k2y = py + ux * h
    return if (k1x >= k2x) k1x to k1y else k2x to k2y
}

/** Spoked rotating wheel — tire, rim and three spokes. */
private fun wheel(c: Canvas, cx: Float, cy: Float, r: Float, angleRad: Float) {
    disc(c, cx, cy, r, r * 0.17f, GEAR)
    disc(c, cx, cy, r * 0.86f, r * 0.09f, ACCENT_DIM)
    for (i in 0 until 3) {
        val a = angleRad + i * (PI.toFloat() / 3f)
        val dx = cos(a) * r * 0.82f
        val dy = sin(a) * r * 0.82f
        line(c, cx - dx, cy - dy, cx + dx, cy + dy, r * 0.07f, ACCENT_DIM)
    }
    dot(c, cx, cy, r * 0.12f, ACCENT)
}

/**
 * Side-view cyclist. Wheels turn twice per cycle, the crank once, and the legs
 * follow the pedals through two-bone IK — a real pedalling motion.
 */
private fun drawCyclist(c: Canvas, frame: Int) {
    val t = frame / SPRITE_FRAMES.toFloat()
    val wheelAngle = t * 4f * PI.toFloat()
    val crank = t * 2f * PI.toFloat() + 0.4f
    val rearX = 0.27f
    val frontX = 0.76f
    val hubY = 0.71f
    val wr = 0.150f

    wheel(c, rearX, hubY, wr, wheelAngle)
    wheel(c, frontX, hubY, wr, wheelAngle)

    // Frame: rear hub -> bottom bracket -> saddle triangle, top tube, fork.
    val bbX = 0.49f
    val bbY = 0.71f
    val seatX = 0.405f
    val seatY = 0.50f
    val headX = 0.665f
    val headY = 0.485f
    val fw = 0.030f
    chain(c, floatArrayOf(rearX, bbX, seatX, rearX), floatArrayOf(hubY, bbY, seatY, hubY), fw, ACCENT)
    line(c, bbX, bbY, headX, headY, fw, ACCENT)
    line(c, seatX, seatY, headX, headY, fw, ACCENT)
    line(c, headX, headY, frontX, hubY, fw, ACCENT)
    line(c, headX, headY, headX - 0.015f, headY - 0.035f, fw, ACCENT)
    line(c, headX - 0.055f, headY - 0.045f, headX + 0.035f, headY - 0.028f, fw * 1.1f, ACCENT)

    // Crank + chainring.
    disc(c, bbX, bbY, 0.035f, 0.016f, GEAR)
    line(c, bbX, bbY, bbX + 0.065f * cos(crank), bbY + 0.065f * sin(crank), 0.020f, GEAR)

    val hipX = 0.425f
    val hipY = 0.475f
    val l1 = 0.155f
    val l2 = 0.185f

    // Far leg (half a crank revolution behind, dimmer, drawn under the frame).
    val farX = bbX + 0.065f * cos(crank + PI.toFloat())
    val farY = bbY + 0.065f * sin(crank + PI.toFloat())
    val (fkX, fkY) = kneeOf(hipX - 0.012f, hipY, farX, farY, l1, l2)
    chain(c, floatArrayOf(hipX - 0.012f, fkX, farX), floatArrayOf(hipY, fkY, farY), 0.050f, ACCENT_DIM)
    line(c, farX, farY, farX + 0.035f, farY, 0.028f, ACCENT_DIM)

    // Far arm behind the torso.
    line(c, 0.575f, 0.325f, 0.700f, 0.455f, 0.038f, ACCENT_DIM)

    // Torso leans onto the bars.
    line(c, hipX, hipY, 0.575f, 0.325f, 0.075f, ACCENT)
    dot(c, 0.428f, 0.492f, 0.030f, GEAR)

    // Near leg riding the pedal.
    val pX = bbX + 0.065f * cos(crank)
    val pY = bbY + 0.065f * sin(crank)
    val (kX, kY) = kneeOf(hipX, hipY, pX, pY, l1, l2)
    chain(c, floatArrayOf(hipX, kX, pX), floatArrayOf(hipY, kY, pY), 0.055f, ACCENT)
    line(c, pX, pY, pX + 0.040f, pY, 0.030f, ACCENT)

    // Near arm to the bars.
    line(c, 0.575f, 0.325f, 0.700f, 0.455f, 0.042f, ACCENT)

    // Head with helmet and short visor.
    val hX = 0.605f
    val hY = 0.263f
    dot(c, hX, hY, 0.053f, ACCENT)
    val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = GEAR }
    c.drawArc(hX - 0.058f, hY - 0.058f, hX + 0.058f, hY + 0.058f, 195f, 150f, true, hp)
    line(c, hX + 0.050f, hY - 0.012f, hX + 0.085f, hY - 0.004f, 0.016f, GEAR)
}

/**
 * Side-view runner / walker. One full stride per cycle: legs swing and fold,
 * arms counter-swing, the body bobs twice per cycle.
 */
private fun drawRunner(c: Canvas, frame: Int, swing: Float, lean: Float, bob: Float, armSwing: Float) {
    val theta = frame / SPRITE_FRAMES.toFloat() * 2f * PI.toFloat()
    val bobY = bob * cos(2f * theta)
    val hipX = 0.470f
    val hipY = 0.535f + bobY
    val shoulderX = hipX + lean * 1.6f
    val shoulderY = 0.355f + bobY * 1.2f
    val l1 = 0.150f
    val l2 = 0.180f
    val la = 0.105f
    val lb = 0.095f

    // Far leg.
    val phiF = swing * sin(theta + PI.toFloat())
    val bendF = (0.9f * (0.5f - 0.5f * cos(theta + PI.toFloat() + 0.9f)) + 0.25f) *
        (if (swing > 0.45f) 1f else 0.6f)
    val kFx = hipX - 0.015f + l1 * sin(phiF)
    val kFy = hipY + l1 * cos(phiF)
    val sigmaF = phiF - bendF
    val fFx = kFx + l2 * sin(sigmaF)
    val fFy = kFy + l2 * cos(sigmaF)
    chain(c, floatArrayOf(hipX - 0.015f, kFx, fFx), floatArrayOf(hipY, kFy, fFy), 0.048f, ACCENT_DIM)

    // Far arm.
    val betaF = armSwing * sin(theta)
    val eFx = shoulderX + la * sin(betaF)
    val eFy = shoulderY + la * cos(betaF)
    val hFx = eFx + lb * sin(betaF - 1.15f)
    val hFy = eFy + lb * cos(betaF - 1.15f)
    chain(c, floatArrayOf(shoulderX, eFx, hFx), floatArrayOf(shoulderY, eFy, hFy), 0.036f, ACCENT_DIM)

    // Torso.
    line(c, hipX, hipY, shoulderX, shoulderY, 0.070f, ACCENT)

    // Near leg.
    val phiN = swing * sin(theta)
    val bendN = (0.9f * (0.5f - 0.5f * cos(theta + 0.9f)) + 0.25f) *
        (if (swing > 0.45f) 1f else 0.6f)
    val kNx = hipX + l1 * sin(phiN)
    val kNy = hipY + l1 * cos(phiN)
    val sigmaN = phiN - bendN
    val fNx = kNx + l2 * sin(sigmaN)
    val fNy = kNy + l2 * cos(sigmaN)
    chain(c, floatArrayOf(hipX, kNx, fNx), floatArrayOf(hipY, kNy, fNy), 0.055f, ACCENT)
    line(c, fNx, fNy, fNx + 0.035f, fNy - 0.008f, 0.026f, ACCENT)

    // Near arm.
    val betaN = armSwing * sin(theta + PI.toFloat())
    val eNx = shoulderX + la * sin(betaN)
    val eNy = shoulderY + la * cos(betaN)
    val hNx = eNx + lb * sin(betaN - 1.15f)
    val hNy = eNy + lb * cos(betaN - 1.15f)
    chain(c, floatArrayOf(shoulderX, eNx, hNx), floatArrayOf(shoulderY, eNy, hNy), 0.040f, ACCENT)

    // Head with a small cap.
    val hX = shoulderX + lean * 1.2f + 0.018f
    val hY = shoulderY - 0.075f
    dot(c, hX, hY, 0.052f, ACCENT)
    val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = GEAR }
    c.drawArc(hX - 0.056f, hY - 0.056f, hX + 0.056f, hY + 0.056f, 200f, 140f, true, hp)
}

/** Walker with a backpack and a trekking pole that plants with each stride. */
private fun drawHiker(c: Canvas, frame: Int) {
    drawRunner(c, frame, swing = 0.32f, lean = 0.03f, bob = 0.006f, armSwing = 0.45f)
    val theta = frame / SPRITE_FRAMES.toFloat() * 2f * PI.toFloat()
    val bobY = 0.006f * cos(2f * theta)
    val shoulderX = 0.470f + 0.03f * 1.6f
    val shoulderY = 0.355f + bobY * 1.2f

    // Backpack high on the back.
    val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GEAR }
    c.drawRoundRect(
        shoulderX - 0.115f,
        shoulderY - 0.075f,
        shoulderX - 0.005f,
        shoulderY + 0.085f,
        0.030f,
        0.030f,
        bp,
    )

    // Trekking pole: tip swings forward as the opposite leg strides.
    val tipX = 0.545f + 0.115f * sin(theta + PI.toFloat())
    val gripX = 0.470f - 0.105f * 0.4f + 0.03f * sin(theta)
    val gripY = shoulderY + 0.145f
    line(c, gripX, gripY, tipX, 0.845f, 0.020f, ACCENT)
    val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.016f
        color = ACCENT
        strokeCap = Paint.Cap.ROUND
    }
    c.drawLine(gripX - 0.030f, gripY, gripX + 0.030f, gripY + 0.008f, cp)
}

/** Ghost rider: translucent silhouette, single pose, readable on any map. */
fun ghostSpriteBitmapFor(context: Context, sport: Sport): Bitmap {
    val d = context.resources.displayMetrics.density
    val s = (30f * d).roundToInt().coerceAtLeast(72)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.save()
    val k = s.toFloat()
    c.scale(k, k)
    when (sport) {
        Sport.BIKE -> drawCyclist(c, 0)
        Sport.RUN -> drawRunner(c, 0, swing = 0.62f, lean = 0.10f, bob = 0.0f, armSwing = 0.95f)
        Sport.WALK -> drawRunner(c, 4, swing = 0.34f, lean = 0.0f, bob = 0.0f, armSwing = 0.55f)
        Sport.HIKE -> drawHiker(c, 4)
    }
    c.restore()
    // Recolor everything to the ghost tone, keeping alpha shape.
    val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE8EEEA
        alpha = 150
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), tint)
    return bmp
}
