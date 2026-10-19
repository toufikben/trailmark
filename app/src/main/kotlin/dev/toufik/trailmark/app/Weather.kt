package dev.toufik.trailmark.app

import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
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

/** Best contiguous span of hours at a destination: label "14:00–17:00" plus aggregates. */
data class WeatherWindow(
    val label: String,
    val tempC: Double,
    val windKmh: Double,
    val code: Int,
)

/** Open-Meteo current conditions (free, no key); cached ~10 min per rounded point. */
object WeatherService {
    private const val TTL_MS = 10 * 60 * 1000L
    private val cache = HashMap<String, Pair<Long, WeatherNow?>>()
    private val windowCache = HashMap<String, Pair<Long, WeatherWindow?>>()

    suspend fun at(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[key] }
        if (cached != null && now - cached.first < TTL_MS) return@withContext cached.second
        val fetched = runCatching { fetch(lat, lon) }.getOrNull()
        synchronized(cache) { cache[key] = now to fetched }
        fetched
    }

    /** Best contiguous [windowSize]-hour span among the next [hoursAhead] hours at a point. */
    suspend fun bestWindow(
        lat: Double,
        lon: Double,
        hoursAhead: Int = 24,
        windowSize: Int = 3,
    ): WeatherWindow? = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "win:%.2f,%.2f", lat, lon)
        val now = System.currentTimeMillis()
        val cached = synchronized(windowCache) { windowCache[key] }
        if (cached != null && now - cached.first < TTL_MS) return@withContext cached.second
        val fetched = runCatching { fetchWindow(lat, lon, hoursAhead, windowSize) }.getOrNull()
        synchronized(windowCache) { windowCache[key] = now to fetched }
        fetched
    }

    private fun severity(code: Int): Int = when (conditionOf(code)) {
        WeatherCondition.CLEAR, WeatherCondition.PARTLY -> 0
        WeatherCondition.CLOUDY -> 1
        WeatherCondition.FOG -> 2
        WeatherCondition.DRIZZLE -> 3
        WeatherCondition.RAIN -> 4
        WeatherCondition.SNOW -> 5
        WeatherCondition.STORM -> 6
    }

    private fun score(tempC: Double, windKmh: Double, precipMm: Double, worstCode: Int): Double =
        severity(worstCode) * 4.0 + precipMm * 2.0 + windKmh / 15.0 +
            kotlin.math.abs(tempC - 18.0) / 10.0

    private fun fetchWindow(lat: Double, lon: Double, hoursAhead: Int, windowSize: Int): WeatherWindow? {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&hourly=temperature_2m,precipitation,weather_code,wind_speed_10m" +
                    "&forecast_days=2&wind_speed_unit=kmh",
                lat,
                lon,
            ),
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val hourly = JSONObject(body).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val precip = hourly.getJSONArray("precipitation")
            val winds = hourly.getJSONArray("wind_speed_10m")
            val codes = hourly.getJSONArray("weather_code")
            val n = times.length()
            if (n < windowSize) return null

            val todayPrefix = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(java.util.Date())
            val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            var first = -1
            for (i in 0 until n) {
                val stamp = times.getString(i)
                val h = stamp.substring(11, 13).toIntOrNull() ?: 0
                if (stamp.startsWith(todayPrefix) && h >= nowHour) {
                    first = i
                    break
                }
            }
            if (first < 0) {
                first = (0 until n).firstOrNull { !times.getString(it).startsWith(todayPrefix) } ?: 0
            }
            val maxStart = minOf(n - windowSize, first + hoursAhead - 1)
            var best: WeatherWindow? = null
            var bestScore = Double.MAX_VALUE
            for (i in first..maxStart) {
                var tempSum = 0.0
                var windSum = 0.0
                var precipSum = 0.0
                var worst = 0
                for (j in i until i + windowSize) {
                    tempSum += temps.getDouble(j)
                    windSum += winds.getDouble(j)
                    precipSum += precip.getDouble(j)
                    val c = codes.getInt(j)
                    if (severity(c) > severity(worst)) worst = c
                }
                val temp = tempSum / windowSize
                val wind = windSum / windowSize
                val s = score(temp, wind, precipSum, worst)
                if (s < bestScore) {
                    bestScore = s
                    val startHour = times.getString(i).substring(11, 13).toIntOrNull() ?: 0
                    val endHour = (startHour + windowSize) % 24
                    best = WeatherWindow(
                        label = String.format(Locale.US, "%02d:00–%02d:00", startHour, endHour),
                        tempC = temp,
                        windKmh = wind,
                        code = worst,
                    )
                }
            }
            return best
        } finally {
            conn.disconnect()
        }
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
