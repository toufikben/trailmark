package dev.toufik.trailmark.app.plan

import dev.toufik.trailmark.core.geo.ElevPoint
import dev.toufik.trailmark.core.geo.RouteChunk
import dev.toufik.trailmark.core.geo.ascentDescent
import dev.toufik.trailmark.core.geo.estimateDurationSeconds
import dev.toufik.trailmark.core.geo.gradeChunks
import dev.toufik.trailmark.core.geo.polylineLengthMeters
import dev.toufik.trailmark.engine.Sport
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * A route produced by the planner. [direct] means no road-following data was
 * available (offline or routing server unreachable) and the geometry is a
 * straight line with no elevation.
 */
data class PlannedRoute(
    val points: List<ElevPoint>,
    val distanceM: Double,
    val ascentM: Double,
    val descentM: Double,
    val chunks: List<RouteChunk>,
    val direct: Boolean,
)

/**
 * Fetches road-following routes with elevation from the public BRouter server
 * and falls back to a straight-line estimate when that fails. Runs on the
 * caller's thread; call from `Dispatchers.IO`.
 */
object RoutePlanner {

    fun plan(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        sport: Sport,
    ): PlannedRoute = plan(listOf(startLat to startLon, destLat to destLon), sport)

    /**
     * Routes through every waypoint in order. Two or more points are required;
     * with fewer the result is an empty direct route.
     */
    fun plan(waypoints: List<Pair<Double, Double>>, sport: Sport): PlannedRoute {
        if (waypoints.size < 2) return directChain(waypoints)
        val online = runCatching { fetchBrouter(waypoints, sport) }.getOrNull()
        return online ?: directChain(waypoints)
    }

    /** Estimated moving time for a route under a sport's flat speed + climb penalty. */
    fun estimateSeconds(route: PlannedRoute, sport: Sport): Double {
        val (flatSpeedKmh, climbPenaltySecPerM) = when (sport) {
            Sport.WALK -> 4.5 to 7.2
            Sport.RUN -> 10.0 to 5.0
            Sport.BIKE -> 17.0 to 4.5
            Sport.HIKE -> 4.0 to 9.0
        }
        return estimateDurationSeconds(
            route.distanceM,
            route.ascentM,
            flatSpeedKmh,
            climbPenaltySecPerM,
        )
    }

    private fun profileFor(sport: Sport): String = when (sport) {
        Sport.WALK, Sport.HIKE -> "trekking"
        Sport.RUN -> "shortest"
        Sport.BIKE -> "fastbike"
    }

    private fun fetchBrouter(
        waypoints: List<Pair<Double, Double>>,
        sport: Sport,
    ): PlannedRoute {
        val lonlats = waypoints.joinToString("|") { (lat, lon) ->
            String.format(Locale.US, "%f,%f", lon, lat)
        }
        val url = "https://brouter.de/brouter?lonlats=$lonlats" +
            "&profile=${profileFor(sport)}&alternativeidx=0&format=geojson"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Trailmark/1.0 (Android)")
            setRequestProperty("Accept", "application/geo+json, application/json")
        }
        val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        if (!body.trimStart().startsWith("{")) return missing()
        val root = JSONObject(body)
        val coords = root.getJSONArray("features")
            .getJSONObject(0)
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
        if (coords.length() < 2) return missing()
        val pts = (0 until coords.length()).map { i ->
            val c = coords.getJSONArray(i)
            val ele = if (c.length() > 2) c.getDouble(2) else 0.0
            ElevPoint(lat = c.getDouble(1), lon = c.getDouble(0), eleM = ele)
        }
        val distance = polylineLengthMeters(pts)
        val (up, down) = ascentDescent(pts)
        return PlannedRoute(
            points = pts,
            distanceM = distance,
            ascentM = up,
            descentM = down,
            chunks = gradeChunks(pts),
            direct = false,
        )
    }

    private fun missing(): PlannedRoute = throw IllegalStateException("no route")

    private fun directChain(waypoints: List<Pair<Double, Double>>): PlannedRoute {
        val pts = ArrayList<ElevPoint>()
        for (i in 1 until waypoints.size) {
            val (a, b) = waypoints[i - 1] to waypoints[i]
            val n = 24
            val from = if (i == 1) 0 else 1
            for (k in from..n) {
                val f = k / n.toDouble()
                pts.add(
                    ElevPoint(
                        lat = a.first + (b.first - a.first) * f,
                        lon = a.second + (b.second - a.second) * f,
                        eleM = 0.0,
                    ),
                )
            }
        }
        return PlannedRoute(
            points = pts,
            distanceM = polylineLengthMeters(pts),
            ascentM = 0.0,
            descentM = 0.0,
            chunks = gradeChunks(pts),
            direct = true,
        )
    }
}
