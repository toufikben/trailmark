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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Frames per pulse cycle for every sport marker. */
const val SPRITE_FRAMES = 8

fun spriteImageId(sport: Sport, frame: Int, style: PuckStyle): String =
    "me-${sport.name}-${style.key}-$frame"

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
 * A top-down puck: bright accent core in a white casing, with a dark chevron that
 * always points to the top of the bitmap. The map rotates it by the live bearing,
 * so the arrow follows the real direction of travel.
 */
fun spriteBitmap(context: Context, sport: Sport, frame: Int, style: PuckStyle): Bitmap {
    val d = context.resources.displayMetrics.density
    val s = (PLAYER_MARKER_DP * d).roundToInt().coerceAtLeast(96)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    drawPuck(Canvas(bmp), s.toFloat(), sport, frame, ghost = false, style = style)
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

private fun drawPuck(
    c: Canvas,
    s: Float,
    sport: Sport,
    frame: Int,
    ghost: Boolean,
    style: PuckStyle = PuckStyle.ARROW,
) {
    val cx = s / 2f
    val cy = s / 2f
    val r = s * 0.395f
    val pulse = ((sin(frame / SPRITE_FRAMES.toFloat() * 2f * PI.toFloat()) + 1.0) / 2.0).toFloat()

    if (ghost) {
        // A faint halo keeps the outline visible over dark and light map styles.
        c.drawCircle(cx, cy, r * 1.12f, paint(0x22E8EEEA))
        c.drawCircle(cx, cy, r, paint(0x33E8EEEA))
    } else {
        // Player: the chosen shape in a white casing, so it stays readable on
        // any map style. The map rotates the sprite by the live bearing.
        PuckArt.draw(c, cx, cy, r, style, pulse)
        return
    }

    val ring = paint(GHOST_TONE, Paint.Style.STROKE).apply {
        strokeWidth = s * 0.040f
        alpha = 205
        pathEffect = DashPathEffect(floatArrayOf(s * 0.060f, s * 0.045f), 0f)
    }
    c.drawCircle(cx, cy, r * 0.93f, ring)

    // Forward nose: top of the bitmap = current bearing.
    val nose = Path().apply {
        moveTo(cx, s * 0.012f)
        lineTo(cx - s * 0.078f, s * 0.148f)
        lineTo(cx + s * 0.078f, s * 0.148f)
        close()
    }
    c.drawPath(nose, paint(GHOST_TONE).apply { alpha = 170 })

    c.save()
    c.translate(cx, cy)
    c.scale(s, s)
    drawGlyph(c, sport, ghost = true)
    c.restore()
}

/** Simple, readable top-down glyph inside the puck. Coordinates are 0..1 of the bitmap. */
private fun drawGlyph(c: Canvas, sport: Sport, ghost: Boolean, inkOverride: Int? = null) {
    val ink = inkOverride ?: if (ghost) GHOST_TONE else PLAYER_GLYPH
    val alpha = if (inkOverride != null) 255 else if (ghost) 190 else 255
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

/**
 * The live position marker, drawn straight onto a canvas so the map sprites and
 * the exported video share one implementation. The caller centres it and, on
 * the map, rotates the canvas to the direction of travel first.
 */
object PuckArt {
    fun draw(c: Canvas, cx: Float, cy: Float, r: Float, style: PuckStyle, pulse: Float = 0f) {
        c.drawCircle(cx, cy, r * (1.14f + 0.05f * pulse), paint(0x33C6FF3D))
        c.drawCircle(cx, cy, r, paint(0xFFF7FAF2.toInt()))
        c.drawCircle(cx, cy, r * 0.78f, paint(ACCENT))
        val ink = paint(PLAYER_INK)
        when (style) {
            PuckStyle.ARROW -> {
                val arrow = Path().apply {
                    moveTo(cx, cy - r * 0.52f)
                    lineTo(cx - r * 0.40f, cy + r * 0.26f)
                    lineTo(cx, cy + r * 0.02f)
                    lineTo(cx + r * 0.40f, cy + r * 0.26f)
                    close()
                }
                c.drawPath(arrow, ink)
            }
            PuckStyle.RUNNER -> glyphSpace(c, cx, cy, r * 1.45f) {
                drawGlyph(c, Sport.RUN, ghost = false, inkOverride = PLAYER_INK)
            }
            PuckStyle.BICYCLE -> glyphSpace(c, cx, cy, r * 1.45f) {
                drawGlyph(c, Sport.BIKE, ghost = false, inkOverride = PLAYER_INK)
            }
            PuckStyle.STAR -> glyphSpace(c, cx, cy, r * 1.00f) { drawStar(c, ink) }
            PuckStyle.RINGS -> glyphSpace(c, cx, cy, r * 1.10f) { drawRings(c, ink) }
            PuckStyle.FLAME -> glyphSpace(c, cx, cy, r * 0.90f) { drawFlame(c, ink) }
            PuckStyle.COMPASS -> glyphSpace(c, cx, cy, r * 0.85f) { drawCompass(c, ink) }
        }
    }

    /** Runs [block] in a 0..1 square centred on the puck, so glyphs stay vector. */
    private inline fun glyphSpace(c: Canvas, cx: Float, cy: Float, half: Float, block: () -> Unit) {
        c.save()
        c.translate(cx - half, cy - half)
        c.scale(half * 2f, half * 2f)
        block()
        c.restore()
    }

    private fun drawStar(c: Canvas, ink: Paint) {
        val star = Path()
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0)
            val rr = if (i % 2 == 0) 0.30f else 0.135f
            val x = 0.5f + (rr * cos(a)).toFloat()
            val y = 0.52f + (rr * sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        c.drawPath(star, ink)
    }

    private fun drawRings(c: Canvas, ink: Paint) {
        val ring = Paint(ink).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        ring.strokeWidth = 0.075f
        c.drawCircle(0.5f, 0.5f, 0.235f, ring)
        ring.strokeWidth = 0.060f
        c.drawCircle(0.5f, 0.5f, 0.100f, ring)
    }

    private fun drawFlame(c: Canvas, ink: Paint) {
        val flame = Path().apply {
            moveTo(0.50f, 0.10f)
            cubicTo(0.68f, 0.28f, 0.74f, 0.44f, 0.68f, 0.58f)
            cubicTo(0.64f, 0.68f, 0.56f, 0.70f, 0.53f, 0.80f)
            cubicTo(0.40f, 0.72f, 0.30f, 0.60f, 0.33f, 0.47f)
            cubicTo(0.35f, 0.37f, 0.43f, 0.33f, 0.45f, 0.20f)
            cubicTo(0.48f, 0.27f, 0.50f, 0.31f, 0.52f, 0.35f)
            close()
        }
        c.drawPath(flame, ink)
    }

    private fun drawCompass(c: Canvas, ink: Paint) {
        val needle = Path().apply {
            moveTo(0.50f, 0.09f)
            lineTo(0.60f, 0.44f)
            lineTo(0.50f, 0.50f)
            lineTo(0.40f, 0.44f)
            close()
            moveTo(0.50f, 0.91f)
            lineTo(0.40f, 0.56f)
            lineTo(0.50f, 0.50f)
            lineTo(0.60f, 0.56f)
            close()
        }
        c.drawPath(needle, ink)
        c.drawCircle(0.50f, 0.50f, 0.045f, ink)
    }
}
