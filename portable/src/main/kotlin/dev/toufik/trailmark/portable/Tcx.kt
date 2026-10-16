package dev.toufik.trailmark.portable

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import java.time.Instant

/** TCX (Garmin) export. Minimal but schema-shaped: one activity, one lap, one track. */
object Tcx {
    fun write(points: List<StoredPoint>, startWallMillis: Long, sport: String = "Running"): String {
        val route = points.filter { it.type != WaypointType.REJECTED }
        val t0 = route.firstOrNull()?.timeNanos ?: 0L
        val tLast = route.lastOrNull()?.timeNanos ?: 0L
        val totalSeconds = (tLast - t0) / 1_000_000_000L
        var distance = 0.0
        for (i in 1 until route.size) {
            distance += haversineMeters(
                route[i - 1].lat, route[i - 1].lon, route[i].lat, route[i].lon,
            )
        }
        val start = Instant.ofEpochMilli(startWallMillis)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">\n")
        sb.append("<Activities><Activity Sport=\"").append(sport).append("\">\n")
        sb.append("<Id>").append(start).append("</Id>\n")
        sb.append("<Lap StartTime=\"").append(start).append("\">\n")
        sb.append("<TotalTimeSeconds>").append(totalSeconds).append("</TotalTimeSeconds>\n")
        sb.append("<DistanceMeters>").append(distance).append("</DistanceMeters>\n")
        sb.append("<Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("<Track>\n")
        for (p in route) {
            val ms = startWallMillis + (p.timeNanos - t0) / 1_000_000L
            sb.append("<Trackpoint><Time>").append(Instant.ofEpochMilli(ms)).append("</Time>")
            sb.append("<Position><LatitudeDegrees>").append(p.lat)
                .append("</LatitudeDegrees><LongitudeDegrees>").append(p.lon)
                .append("</LongitudeDegrees></Position>")
            p.altitudeM?.let { sb.append("<AltitudeMeters>").append(it).append("</AltitudeMeters>") }
            sb.append("</Trackpoint>\n")
        }
        sb.append("</Track>\n</Lap>\n</Activity></Activities>\n</TrainingCenterDatabase>\n")
        return sb.toString()
    }
}
