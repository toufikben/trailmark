package dev.toufik.trailmark.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.toufik.trailmark.engine.Sport
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Frames per pulse cycle for every sport marker. */
const val SPRITE_FRAMES = 8

fun spriteImageId(sport: Sport, frame: Int): String = "me-${sport.name}-$frame"

/** Logical diameter of the live player puck, in dp. */
const val PLAYER_MARKER_DP = 38f

/** Logical diameter of the ghost puck, in dp. */
const val GHOST_MARKER_DP = 34f

private const val ACCENT = 0xFFC6FF3D.toInt()
private const val PLAYER_INK = 0xE60E1113.toInt()
private const val PLAYER_GLYPH = 0xFFF4F8EE.toInt()
private const val GHOST_TONE = 0xFFE8EEEA.toInt()

private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.style = style
    }

/**
 * A top-down puck: dark disc, accent ring and a forward nose that always points
 * to the top of the bitmap. The map rotates it by the live bearing, so the nose
 * follows the real direction of travel.
 */
fun spriteBitmap(context: Context, sport: Sport, frame: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val s = (PLAYER_MARKER_DP * d).roundToInt().coerceAtLeast(96)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    drawPuck(Canvas(bmp), s.toFloat(), sport, frame, ghost = false)
    return bmp
}

/** The same puck as a translucent outline: clearly a rival, never a solid clone. */
fun ghostSpriteBitmapFor(context: Context, sport: Sport): Bitmap {
    val d = context.resources.displayMetrics.density
    val s = (GHOST_MARKER_DP * d).roundToInt().coerceAtLeast(72)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    drawPuck(Canvas(bmp), s.toFloat(), sport, frame = 0, ghost = true)
    return bmp
}

private fun drawPuck(c: Canvas, s: Float, sport: Sport, frame: Int, ghost: Boolean) {
    val cx = s / 2f
    val cy = s / 2f
    val r = s * 0.395f
    val pulse = ((sin(frame / SPRITE_FRAMES.toFloat() * 2f * PI.toFloat()) + 1.0) / 2.0).toFloat()

    if (ghost) {
        // A faint halo keeps the outline visible over dark and light map styles.
        c.drawCircle(cx, cy, r * 1.12f, paint(0x22E8EEEA))
        c.drawCircle(cx, cy, r, paint(0x33E8EEEA))
    } else {
        c.drawCircle(cx, cy, r * (1.14f + 0.05f * pulse), paint(0x28C6FF3D))
        c.drawCircle(cx, cy, r, paint(PLAYER_INK))
    }

    val ring = paint(if (ghost) GHOST_TONE else ACCENT, Paint.Style.STROKE).apply {
        strokeWidth = s * 0.040f
        if (ghost) {
            alpha = 205
            pathEffect = DashPathEffect(floatArrayOf(s * 0.060f, s * 0.045f), 0f)
        }
    }
    c.drawCircle(cx, cy, r * 0.93f, ring)

    // Forward nose: top of the bitmap = current bearing.
    val nose = Path().apply {
        moveTo(cx, s * 0.012f)
        lineTo(cx - s * 0.078f, s * 0.148f)
        lineTo(cx + s * 0.078f, s * 0.148f)
        close()
    }
    c.drawPath(nose, paint(if (ghost) GHOST_TONE else ACCENT).apply {
        if (ghost) alpha = 170
    })

    c.save()
    c.translate(cx, cy)
    c.scale(s, s)
    drawGlyph(c, sport, ghost)
    c.restore()
}

/** Simple, readable top-down glyph inside the puck. Coordinates are 0..1 of the bitmap. */
private fun drawGlyph(c: Canvas, sport: Sport, ghost: Boolean) {
    val ink = if (ghost) GHOST_TONE else PLAYER_GLYPH
    val alpha = if (ghost) 190 else 255
    val body = paint(ink).apply { this.alpha = alpha }
    val limb = paint(ink, Paint.Style.STROKE).apply {
        this.alpha = alpha
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        limb.strokeWidth = w
        c.drawLine(x1, y1, x2, y2, limb)
    }
    fun circle(x: Float, y: Float, radius: Float) {
        c.drawCircle(x, y, radius, body)
    }

    when (sport) {
        Sport.BIKE -> {
            val wheel = paint(ink, Paint.Style.STROKE).apply {
                this.alpha = alpha
                strokeWidth = 0.030f
            }
            c.drawOval(RectF(0.433f, 0.300f, 0.567f, 0.410f), wheel)
            c.drawOval(RectF(0.433f, 0.600f, 0.567f, 0.710f), wheel)
            line(0.50f, 0.410f, 0.50f, 0.600f, 0.030f)
            line(0.385f, 0.465f, 0.615f, 0.465f, 0.030f)
            line(0.435f, 0.565f, 0.565f, 0.565f, 0.030f)
            circle(0.50f, 0.505f, 0.047f)
        }

        Sport.WALK -> {
            circle(0.50f, 0.372f, 0.052f)
            line(0.50f, 0.430f, 0.50f, 0.552f, 0.078f)
            line(0.50f, 0.458f, 0.412f, 0.532f, 0.050f)
            line(0.50f, 0.458f, 0.588f, 0.532f, 0.050f)
            line(0.50f, 0.548f, 0.455f, 0.668f, 0.062f)
            line(0.50f, 0.548f, 0.545f, 0.668f, 0.062f)
        }

        Sport.RUN -> {
            circle(0.50f, 0.362f, 0.052f)
            line(0.50f, 0.422f, 0.50f, 0.548f, 0.080f)
            line(0.50f, 0.452f, 0.390f, 0.548f, 0.052f)
            line(0.50f, 0.452f, 0.610f, 0.548f, 0.052f)
            line(0.50f, 0.540f, 0.425f, 0.678f, 0.064f)
            line(0.50f, 0.540f, 0.582f, 0.650f, 0.064f)
        }

        Sport.HIKE -> {
            circle(0.50f, 0.362f, 0.052f)
            line(0.50f, 0.422f, 0.50f, 0.548f, 0.080f)
            line(0.50f, 0.452f, 0.390f, 0.548f, 0.052f)
            line(0.50f, 0.452f, 0.610f, 0.548f, 0.052f)
            line(0.50f, 0.540f, 0.425f, 0.678f, 0.064f)
            line(0.50f, 0.540f, 0.582f, 0.650f, 0.064f)
            // Backpack behind one shoulder.
            val pack = RectF(0.585f, 0.410f, 0.665f, 0.520f)
            c.drawRoundRect(pack, 0.030f, 0.030f, paint(ink).apply { this.alpha = alpha })
            // Trekking pole.
            line(0.635f, 0.470f, 0.725f, 0.665f, 0.024f)
        }
    }
}
