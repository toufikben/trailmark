package dev.toufik.trailmark.app.maps

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.toufik.trailmark.core.geo.ElevPoint
import dev.toufik.trailmark.core.geo.douglasPeucker
import dev.toufik.trailmark.core.geo.polylineLengthMeters
import dev.toufik.trailmark.portable.Gpx
import dev.toufik.trailmark.portable.ImportedPoint
import java.io.File
import java.util.Locale

/**
 * Planned routes in and out of the app: GPX files from route planners (Komoot,
 * Strava, AllTrails… all export GPX) become saved routes, and a saved or
 * planned route goes out as a GPX file plus a map link anyone can open.
 */
object RouteTransfer {
    /** Detailed planner tracks run to tens of thousands of points. */
    private const val MAX_TRACK_POINTS = 2_000

    /** Parses and saves a GPX route; null when the file holds no usable path. */
    fun importGpx(context: Context, bytes: ByteArray, fallbackName: String): SavedRoute? {
        val route = parseGpx(bytes, fallbackName, System.currentTimeMillis()) ?: return null
        SavedRoutes(context).save(route)
        return route
    }

    internal fun parseGpx(bytes: ByteArray, fallbackName: String, id: Long): SavedRoute? {
        val xml = bytes.toString(Charsets.UTF_8)
        if (!xml.contains("<gpx", ignoreCase = true)) return null
        val raw = Gpx.read(xml)
        if (raw.size < 2) return null
        var track = raw.map { ElevPoint(it.lat, it.lon, it.altitudeM ?: 0.0) }
        var toleranceM = 2.0
        while (track.size > MAX_TRACK_POINTS && toleranceM <= 64.0) {
            track = douglasPeucker(track, toleranceM, { it.lat }, { it.lon })
            toleranceM *= 2
        }
        val climb = track.zipWithNext().sumOf { (a, b) -> (b.eleM - a.eleM).coerceAtLeast(0.0) }
        return SavedRoute(
            id = id,
            name = Gpx.name(xml) ?: fallbackName,
            sport = "HIKE",
            stops = listOf(track.first().lat to track.first().lon, track.last().lat to track.last().lon),
            distanceKm = polylineLengthMeters(track) / 1000.0,
            climbM = climb.toInt(),
            track = track,
        )
    }

    /** Opens the share sheet with the route as a GPX file and a map link. */
    fun share(context: Context, name: String, points: List<ElevPoint>, chooserTitle: String) {
        if (points.size < 2) return
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').take(40).ifEmpty { "route" }
        val file = File(dir, "trailmark-route-$safe.gpx")
        file.writeText(Gpx.writeRoute(points.map { ImportedPoint(it.lat, it.lon, it.eleM, null) }, name))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, name + "\n" + mapsLink(points))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /** Google Maps directions from start to end through up to eight evenly spread points. */
    internal fun mapsLink(points: List<ElevPoint>): String {
        fun f(p: ElevPoint) = String.format(Locale.US, "%.5f,%.5f", p.lat, p.lon)
        val inner = if (points.size > 2) points.subList(1, points.size - 1) else emptyList()
        val n = minOf(8, inner.size)
        val via = (1..n).map { k -> inner[(k * inner.size / (n + 1)).coerceIn(0, inner.size - 1)] }
        val sb = StringBuilder("https://www.google.com/maps/dir/?api=1&origin=")
            .append(f(points.first()))
            .append("&destination=")
            .append(f(points.last()))
        if (via.isNotEmpty()) sb.append("&waypoints=").append(via.joinToString("%7C") { f(it) })
        return sb.append("&travelmode=walking").toString()
    }
}
