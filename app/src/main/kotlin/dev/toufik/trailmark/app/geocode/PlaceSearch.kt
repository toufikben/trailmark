package dev.toufik.trailmark.app.geocode

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One geocoding hit: display name plus WGS84 coordinates. */
data class PlaceResult(
    val name: String,
    val lat: Double,
    val lon: Double,
)

/** Nominatim place search (OpenStreetMap), limited to a handful of hits per query. */
object PlaceSearch {
    suspend fun search(query: String, langCode: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://nominatim.openstreetmap.org/search" +
                    "?format=jsonv2&limit=6" +
                    "&q=" + Uri.encode(query) +
                    "&accept-language=" + Uri.encode(langCode),
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Trailmark/0.1 (Android; dev.toufik.trailmark)")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                if (conn.responseCode != 200) return@runCatching emptyList()
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                    val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                    val display = o.optString("display_name").takeIf { it.isNotBlank() }
                        ?: o.optString("name").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    PlaceResult(display, lat, lon)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(emptyList())
    }
}
