package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.core.geo.douglasPeucker
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import java.io.File
import java.util.Locale

private const val W = 1080
private const val H = 1920
private const val PAD = 96f
private const val CONTENT_W = W - 2 * 96

internal fun sportName(t: Texts, sport: String): String = when (sport) {
    "RUN" -> t.run
    "WALK" -> t.walk
    "BIKE" -> t.bike
    "HIKE" -> t.hike
    else -> sport
}

/**
 * Renders a 1080x1920 share card: dark ground, brand accent, the recorded route
 * as a polyline and the activity's headline numbers. All text goes through
 * StaticLayout so Arabic and the other RTL scripts shape correctly.
 */
internal fun renderStoryCard(
    distanceM: Double,
    elapsedNanos: Long,
    elevationGainM: Double,
    maxSpeedMps: Double,
    fixCount: Long,
    startWallMillis: Long,
    points: List<StoredPoint>,
    t: Texts,
    lang: Lang,
    sport: String,
): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Palette.bg.toArgb())

    val accent = Palette.accent.toArgb()
    val textColor = Palette.text.toArgb()
    val secondary = Palette.textSecondary.toArgb()
    val align = if (lang.rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL

    drawText(canvas, "Trailmark  ·  ${sportName(t, sport)}", 42f, accent, 150f, align, bold = true)
    drawText(canvas, formatDate(startWallMillis, lang), 36f, secondary, 224f, align)

    val distText = formatDistance(distanceM)
    val bigLine = SpannableString("$distText ${t.km}")
    bigLine.setSpan(AbsoluteSizeSpan(168, true), 0, distText.length, 0)
    bigLine.setSpan(AbsoluteSizeSpan(52, true), distText.length, bigLine.length, 0)
    drawText(canvas, bigLine, 168f, accent, 330f, align, bold = true)

    val pace = if (distanceM < 50) null else (elapsedNanos / 1e9) / (distanceM / 1000)
    val stats = listOf(
        t.time to formatDuration(elapsedNanos),
        t.pace to (pace?.let { formatPace(it) } ?: "--:--"),
        t.elevation to "${elevationGainM.toInt()} ${t.metre}",
    )
    val colW = CONTENT_W / 3
    stats.forEachIndexed { i, (label, value) ->
        val col = if (lang.rtl) stats.lastIndex - i else i
        val x = PAD + col * colW
        drawText(canvas, label, 32f, secondary, 620f, align, x = x, width = colW - 24)
        drawText(canvas, value, 58f, textColor, 668f, align, x = x, width = colW - 24, bold = true)
    }

    drawRoute(canvas, points, top = 830f, height = 620f, accent = accent, end = textColor)

    val speedPart = if (maxSpeedMps.isFinite() && maxSpeedMps < 1000.0) {
        "   ·   ${t.maxSpeed} ${String.format(Locale.US, "%.1f", maxSpeedMps * 3.6)} ${t.kmh}"
    } else {
        ""
    }
    drawText(canvas, "${t.points} $fixCount$speedPart", 40f, secondary, 1540f, align)

    drawText(
        canvas, "Trailmark", 46f, accent, 1740f,
        Layout.Alignment.ALIGN_CENTER, x = 0f, width = W, bold = true,
    )
    return bmp
}

private fun drawRoute(
    canvas: Canvas,
    points: List<StoredPoint>,
    top: Float,
    height: Float,
    accent: Int,
    end: Int,
) {
    val route = points.filter { it.type != WaypointType.REJECTED }
    if (route.size < 2) return
    val drawRoute = douglasPeucker(route, 6.0, { it.lat }, { it.lon })

    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    for (p in route) {
        if (p.lat < minLat) minLat = p.lat
        if (p.lat > maxLat) maxLat = p.lat
        if (p.lon < minLon) minLon = p.lon
        if (p.lon > maxLon) maxLon = p.lon
    }
    val latSpan = (maxLat - minLat).coerceAtLeast(1e-9)
    val lonSpan = (maxLon - minLon).coerceAtLeast(1e-9)
    val scale = minOf(CONTENT_W / lonSpan, height / latSpan)
    val xOffset = PAD + (CONTENT_W - lonSpan * scale) / 2f
    val yOffset = top + (height - latSpan * scale) / 2f

    fun px(p: StoredPoint) = Pair(
        (xOffset + (p.lon - minLon) * scale).toFloat(),
        (yOffset + (maxLat - p.lat) * scale).toFloat(),
    )

    val path = Path()
    val (sx, sy) = px(drawRoute.first())
    path.moveTo(sx, sy)
    for (i in 1 until drawRoute.size) {
        val (x, y) = px(drawRoute[i])
        path.lineTo(x, y)
    }

    val line = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = accent
    }
    canvas.drawPath(path, line)

    val dot = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    dot.color = accent
    canvas.drawCircle(sx, sy, 20f, dot)
    val (ex, ey) = px(route.last())
    dot.color = end
    canvas.drawCircle(ex, ey, 20f, dot)
}

private fun drawText(
    canvas: Canvas,
    s: CharSequence,
    size: Float,
    color: Int,
    y: Float,
    align: Layout.Alignment,
    x: Float = PAD,
    width: Int = CONTENT_W,
    bold: Boolean = false,
): Float {
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width)
        .setAlignment(align)
        .setIncludePad(false)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
    return y + layout.height
}

/** Stores the card in the exports cache and opens the system share sheet. */
internal fun shareStoryCardImage(
    context: Context,
    bitmap: Bitmap,
    activityId: Long,
    chooserTitle: String,
) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "trailmark-story-$activityId.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
