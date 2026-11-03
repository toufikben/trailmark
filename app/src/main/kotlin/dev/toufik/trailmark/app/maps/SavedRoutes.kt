package dev.toufik.trailmark.app.maps

import android.content.Context
import dev.toufik.trailmark.core.geo.ElevPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * A route planned in Explore and kept for later reuse. A planned route keeps
 * its [stops] and is routed again on open; an imported one (GPX from Komoot,
 * Strava, …) keeps its exact [track] instead.
 */
data class SavedRoute(
    val id: Long,
    val name: String,
    val sport: String,
    val stops: List<Pair<Double, Double>>,
    val distanceKm: Double,
    val climbM: Int,
    val track: List<ElevPoint> = emptyList(),
)

/** Small JSON-backed store for planned routes; newest first, capped. */
class SavedRoutes(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("trailmark", Context.MODE_PRIVATE)

    fun all(): List<SavedRoute> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return@runCatching emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pts = o.optJSONArray("stops") ?: return@mapNotNull null
            val stops = (0 until pts.length()).mapNotNull { j ->
                val p = pts.optJSONArray(j) ?: return@mapNotNull null
                p.optDouble(0) to p.optDouble(1)
            }
            if (stops.size < 2) return@mapNotNull null
            val trackArr = o.optJSONArray("track")
            val track = if (trackArr == null) {
                emptyList()
            } else {
                (0 until trackArr.length()).mapNotNull { j ->
                    val p = trackArr.optJSONArray(j) ?: return@mapNotNull null
                    ElevPoint(p.optDouble(0), p.optDouble(1), p.optDouble(2, 0.0))
                }
            }
            SavedRoute(
                id = o.optLong("id"),
                name = o.optString("name"),
                sport = o.optString("sport", "RUN"),
                stops = stops,
                distanceKm = o.optDouble("km", 0.0),
                climbM = o.optInt("climb", 0),
                track = track,
            )
        }
    }.getOrDefault(emptyList())

    fun save(route: SavedRoute) {
        val next = (listOf(route) + all().filter { it.id != route.id }).take(MAX)
        persist(next)
    }

    fun delete(id: Long) {
        persist(all().filter { it.id != id })
    }

    private fun persist(routes: List<SavedRoute>) {
        val arr = JSONArray()
        routes.forEach { r ->
            val pts = JSONArray()
            r.stops.forEach { (lat, lon) ->
                pts.put(JSONArray().put(lat).put(lon))
            }
            val o = JSONObject()
                .put("id", r.id)
                .put("name", r.name)
                .put("sport", r.sport)
                .put("km", r.distanceKm)
                .put("climb", r.climbM)
                .put("stops", pts)
            if (r.track.isNotEmpty()) {
                val track = JSONArray()
                r.track.forEach { p -> track.put(JSONArray().put(p.lat).put(p.lon).put(p.eleM)) }
                o.put("track", track)
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "saved_routes"
        const val MAX = 50
    }
}
