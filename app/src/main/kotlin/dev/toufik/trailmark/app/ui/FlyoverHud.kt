package dev.toufik.trailmark.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.distanceUnit
import dev.toufik.trailmark.app.elevationUnit
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatElevation
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.app.formatSpeed
import dev.toufik.trailmark.app.speedUnit

/** Totals shown on the clip, from the activity row and its analysis. */
class FlyoverStats(
    val distanceM: Double,
    val movingNanos: Long,
    val climbM: Double,
    val title: String,
    val subtitle: String,
)

private const val ACCENT = 0xFFC6FF3D.toInt()
private const val INK = 0xFFEEF3EF.toInt()
private const val MUTED = 0xFFB4C0BA.toInt()
private const val SCRIM = 0xB30B0F0E.toInt()

/**
 * Draws the clip's overlays straight onto the encoder canvas: a live stats bar
 * and progress line while the route plays, a title over the intro and a stats
 * card over the outro, plus the map data attribution on every frame.
 */
internal class FlyoverHud(
    private val t: Texts,
    private val stats: FlyoverStats,
    private val runPace: Boolean,
    private val width: Int,
    private val height: Int,
) {
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; textSize = 64f; typeface = bold }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 38f; typeface = bold }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 22f }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 18f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    fun route(c: Canvas, distanceM: Double, movingNanos: Long, speedMps: Double, progress: Double) {
        val top = height - 210f
        fill.color = SCRIM
        c.drawRoundRect(RectF(28f, top, width - 28f, height - 70f), 28f, 28f, fill)
        val col = (width - 56f) / 3f
        cell(c, 28f + col * 0, top, formatDistance(distanceM), distanceUnit(t), accent = true)
        cell(c, 28f + col * 1, top, formatDuration(movingNanos), t.time)
        if (runPace) {
            val pace = if (speedMps > 0.3) formatPace(1000.0 / speedMps) else "--:--"
            cell(c, 28f + col * 2, top, pace, t.pace)
        } else {
            cell(c, 28f + col * 2, top, formatSpeed(speedMps), speedUnit(t))
        }
        // Progress along the route, just under the stats bar.
        fill.color = 0x40EEF3EF
        c.drawRoundRect(RectF(56f, height - 92f, width - 56f, height - 86f), 3f, 3f, fill)
        fill.color = ACCENT
        val x = 56f + (width - 112f) * progress.toFloat().coerceIn(0f, 1f)
        c.drawRoundRect(RectF(56f, height - 92f, x, height - 86f), 3f, 3f, fill)
        attribution(c)
    }

    private fun cell(c: Canvas, x: Float, top: Float, v: String, unit: String, accent: Boolean = false) {
        val p = if (accent) big else value
        val cx = x + (width - 56f) / 6f
        p.textAlign = Paint.Align.CENTER
        label.textAlign = Paint.Align.CENTER
        c.drawText(v, cx, top + 72f, p)
        c.drawText(unit, cx, top + 108f, label)
    }

    fun intro(c: Canvas, tPhase: Double) {
        val a = (255 * (1.0 - easeInOut(((tPhase - 0.55) / 0.45).coerceIn(0.0, 1.0)))).toInt()
        if (a <= 0) return attribution(c)
        fill.color = (a * 0xB3 / 255 shl 24) or 0x0B0F0E
        c.drawRoundRect(RectF(40f, 90f, width - 40f, 260f), 32f, 32f, fill)
        val titleP = Paint(value).apply { textSize = 44f; alpha = a; textAlign = Paint.Align.CENTER }
        val subP = Paint(label).apply { alpha = a; textAlign = Paint.Align.CENTER }
        c.drawText(stats.title, width / 2f, 165f, titleP)
        c.drawText(stats.subtitle, width / 2f, 212f, subP)
        attribution(c)
    }

    fun outro(c: Canvas, tPhase: Double) {
        val k = easeInOut(((tPhase - 0.35) / 0.4).coerceIn(0.0, 1.0)).toFloat()
        if (k > 0f) {
            val cardH = 420f
            val y = height - (cardH + 120f) * k
            fill.color = SCRIM
            c.drawRoundRect(RectF(40f, y, width - 40f, y + cardH), 36f, 36f, fill)
            big.textAlign = Paint.Align.CENTER
            label.textAlign = Paint.Align.CENTER
            value.textAlign = Paint.Align.CENTER
            c.drawText(stats.title, width / 2f, y + 64f, Paint(label).apply { textSize = 26f; color = INK })
            c.drawText(formatDistance(stats.distanceM), width / 2f, y + 170f, Paint(big).apply { textSize = 110f })
            c.drawText(distanceUnit(t), width / 2f, y + 210f, label)
            val third = (width - 80f) / 3f
            val avg = if (stats.movingNanos > 0) stats.distanceM / (stats.movingNanos / 1e9) else 0.0
            val pairs = listOf(
                formatDuration(stats.movingNanos) to t.time,
                (if (runPace && avg > 0.3) formatPace(1000.0 / avg) else formatSpeed(avg)) to
                    (if (runPace) t.pace else speedUnit(t)),
                "+${formatElevation(stats.climbM)}" to elevationUnit(t),
            )
            pairs.forEachIndexed { i, (v, u) ->
                val cx = 40f + third * i + third / 2f
                c.drawText(v, cx, y + 300f, value)
                c.drawText(u, cx, y + 336f, label)
            }
        }
        attribution(c)
    }

    private fun attribution(c: Canvas) {
        small.textAlign = Paint.Align.CENTER
        c.drawText("Trailmark · © OpenStreetMap contributors", width / 2f, height - 30f, small)
    }
}
