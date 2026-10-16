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

    fun read(xml: String): List<ImportedPoint> = TRKPT.findAll(xml).mapNotNull { m ->
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

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
