package dev.toufik.trailmark.app.maps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A route planned in Explore and kept for later reuse. */
data class SavedRoute(
    val id: Long,
    val name: String,
    val sport: String,
    val stops: List<Pair<Double, Double>>,
    val distanceKm: Double,
    val climbM: Int,
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
            SavedRoute(
                id = o.optLong("id"),
                name = o.optString("name"),
                sport = o.optString("sport", "RUN"),
                stops = stops,
                distanceKm = o.optDouble("km", 0.0),
                climbM = o.optInt("climb", 0),
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
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("sport", r.sport)
                    .put("km", r.distanceKm)
                    .put("climb", r.climbM)
                    .put("stops", pts),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "saved_routes"
        const val MAX = 50
    }
}
