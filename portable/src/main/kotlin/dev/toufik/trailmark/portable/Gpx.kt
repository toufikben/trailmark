package dev.toufik.trailmark.portable

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import java.time.Instant

/** One imported track point. */
data class ImportedPoint(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val timeMillis: Long?,
)

/**
 * GPX 1.1 writer/reader. The writer emits rejected points as nothing (they are
 * raw evidence, not path). The reader is a small, dependency-free parser: it
 * tolerates points without `<ele>`/`<time>` and never throws on a missing field.
 */
object Gpx {
    private val TRKPT = Regex(
        """<trkpt\b([^>]*)>(.*?)</trkpt>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val RTEPT = Regex(
        """<rtept\b([^>]*?)(?:/>|>(.*?)</rtept>)""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val NAME = Regex("""<name>([^<]*)</name>""")
    private val TYPE = Regex("""<type>([^<]*)</type>""")
    private val ATTR = Regex("""(\w+)="([^"]*)"""")
    private val ELE = Regex("""<ele>([^<]*)</ele>""")
    private val TIME = Regex("""<time>([^<]*)</time>""")

    fun write(points: List<StoredPoint>, startWallMillis: Long, name: String = "Trailmark"): String {
        val route = points.filter { it.type != WaypointType.REJECTED }
        val t0 = route.firstOrNull()?.timeNanos ?: 0L
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Trailmark\" ")
            .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<trk><name>").append(escape(name)).append("</name><trkseg>\n")
        for (p in route) {
            sb.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
            p.altitudeM?.let { sb.append("<ele>").append(it).append("</ele>") }
            val ms = startWallMillis + (p.timeNanos - t0) / 1_000_000L
            sb.append("<time>").append(Instant.ofEpochMilli(ms).toString()).append("</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    /**
     * Track points, or route points when the file only has a planned `<rte>`
     * (what route planners such as Komoot or Strava often export).
     */
    fun read(xml: String): List<ImportedPoint> {
        val track = readMatches(TRKPT.findAll(xml))
        return track.ifEmpty { readMatches(RTEPT.findAll(xml)) }
    }

    /** The first `<name>` in the file (track or route name), if any. */
    fun name(xml: String): String? =
        NAME.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(::unescape)

    /**
     * The first `<type>` in the file, the free-text activity hint Komoot, Strava
     * and Garmin write ("cycling", "hiking", "Running", …). Null when absent.
     */
    fun type(xml: String): String? =
        TYPE.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(::unescape)

    /** A timeless track, for sharing a planned route. */
    fun writeRoute(points: List<ImportedPoint>, name: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Trailmark\" ")
            .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<trk><name>").append(escape(name)).append("</name><trkseg>\n")
        for (p in points) {
            sb.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
            p.altitudeM?.let { sb.append("<ele>").append(it).append("</ele>") }
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun readMatches(matches: Sequence<MatchResult>): List<ImportedPoint> = matches.mapNotNull { m ->
        val attrs = ATTR.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
        val lat = attrs["lat"]?.toDoubleOrNull() ?: return@mapNotNull null
        val lon = attrs["lon"]?.toDoubleOrNull() ?: return@mapNotNull null
        val body = m.groupValues[2]
        val ele = ELE.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        val time = TIME.find(body)?.groupValues?.get(1)?.let {
            runCatching { Instant.parse(it.trim()).toEpochMilli() }.getOrNull()
        }
        ImportedPoint(lat, lon, ele, time)
    }.toList()

    private fun unescape(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
