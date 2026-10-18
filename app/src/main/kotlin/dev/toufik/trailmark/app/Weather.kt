package dev.toufik.trailmark.app

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** WMO weather code collapsed into the categories the chips show. */
enum class WeatherCondition { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/**
 * Current conditions at a point: temperature, wind speed and the direction the
 * wind blows FROM (meteorological convention, degrees clockwise from north).
 */
data class WeatherNow(
    val tempC: Double,
    val windKmh: Double,
    val windDeg: Int,
    val code: Int,
) {
    val condition: WeatherCondition get() = conditionOf(code)
}

fun conditionOf(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY
    3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.STORM
    else -> WeatherCondition.CLOUDY
}

/** Open-Meteo current conditions (free, no key); cached ~10 min per rounded point. */
object WeatherService {
    private const val TTL_MS = 10 * 60 * 1000L
    private val cache = HashMap<String, Pair<Long, WeatherNow?>>()

    suspend fun at(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[key] }
        if (cached != null && now - cached.first < TTL_MS) return@withContext cached.second
        val fetched = runCatching { fetch(lat, lon) }.getOrNull()
        synchronized(cache) { cache[key] = now to fetched }
        fetched
    }

    private fun fetch(lat: Double, lon: Double): WeatherNow {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,wind_speed_10m,wind_direction_10m,weather_code" +
                    "&wind_speed_unit=kmh",
                lat,
                lon,
            ),
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            return WeatherNow(
                tempC = current.getDouble("temperature_2m"),
                windKmh = current.getDouble("wind_speed_10m"),
                windDeg = current.getInt("wind_direction_10m"),
                code = current.getInt("weather_code"),
            )
        } finally {
            conn.disconnect()
        }
    }
}
