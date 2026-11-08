package dev.toufik.trailmark.app.backup

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.json.JSONArray
import org.json.JSONObject

/**
 * The on-disk shape of a backup: one JSON document listing every activity with
 * its track points and its media, plus the media files themselves stored beside
 * it in the same ZIP. It holds no ids and no file paths, so a backup taken on
 * one install restores cleanly into another.
 */
data class BackupFile(
    val version: Int = VERSION,
    val createdWallMillis: Long,
    val activities: List<BackupActivity>,
) {
    companion object {
        const val VERSION = 1
    }
}

data class BackupActivity(
    val sport: String,
    val startWallMillis: Long,
    val distanceM: Double,
    val elapsedNanos: Long,
    val fixCount: Long,
    val rejectedCount: Long,
    val mockDetected: Boolean,
    val weatherTempC: Double?,
    val weatherWindKmh: Double?,
    val weatherWindDeg: Int?,
    val weatherCode: Int?,
    val points: List<StoredPoint>,
    val media: List<BackupMedia>,
)

data class BackupMedia(
    val entry: String,
    val kind: String,
    val lat: Double?,
    val lon: Double?,
    val bearingDeg: Double?,
    val distanceM: Double,
    val elapsedNanos: Long,
    val createdWallMillis: Long,
    val durationMs: Long?,
    val caption: String?,
)

/**
 * JSON in and out. Kept apart from the ZIP plumbing so the round trip can be
 * tested on the JVM without a device or a ContentResolver.
 */
object BackupCodec {
    private const val FORMAT = "trailmark-backup"

    fun encode(file: BackupFile): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", file.version)
            .put("created", file.createdWallMillis)
        val acts = JSONArray()
        for (a in file.activities) {
            val o = JSONObject()
                .put("sport", a.sport)
                .put("startWallMillis", a.startWallMillis)
                .put("distanceM", a.distanceM)
                .put("elapsedNanos", a.elapsedNanos)
                .put("fixCount", a.fixCount)
                .put("rejectedCount", a.rejectedCount)
                .put("mockDetected", a.mockDetected)
            putNullable(o, "weatherTempC", a.weatherTempC)
            putNullable(o, "weatherWindKmh", a.weatherWindKmh)
            putNullable(o, "weatherWindDeg", a.weatherWindDeg)
            putNullable(o, "weatherCode", a.weatherCode)
            val pts = JSONArray()
            for (p in a.points) {
                val po = JSONObject()
                    .put("lat", p.lat)
                    .put("lon", p.lon)
                    .put("t", p.timeNanos)
                    .put("acc", p.accuracyM)
                    .put("type", p.type.name)
                putNullable(po, "alt", p.altitudeM)
                putNullable(po, "speed", p.speedMps)
                putNullable(po, "bearing", p.bearingDeg)
                pts.put(po)
            }
            o.put("points", pts)
            val media = JSONArray()
            for (m in a.media) {
                val mo = JSONObject()
                    .put("entry", m.entry)
                    .put("kind", m.kind)
                    .put("distanceM", m.distanceM)
                    .put("elapsedNanos", m.elapsedNanos)
                    .put("createdWallMillis", m.createdWallMillis)
                putNullable(mo, "lat", m.lat)
                putNullable(mo, "lon", m.lon)
                putNullable(mo, "bearing", m.bearingDeg)
                putNullable(mo, "durationMs", m.durationMs)
                putNullable(mo, "caption", m.caption)
                media.put(mo)
            }
            o.put("media", media)
            acts.put(o)
        }
        root.put("activities", acts)
        return root.toString()
    }

    /** Null when the text is not a backup this version understands. */
    fun decode(text: String): BackupFile? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT) return null
        val version = root.optInt("version", 0)
        if (version < 1 || version > BackupFile.VERSION) return null
        val acts = root.optJSONArray("activities") ?: JSONArray()
        val out = ArrayList<BackupActivity>(acts.length())
        for (i in 0 until acts.length()) {
            val o = acts.optJSONObject(i) ?: continue
            val pts = ArrayList<StoredPoint>()
            val pArr = o.optJSONArray("points") ?: JSONArray()
            for (j in 0 until pArr.length()) {
                val p = pArr.optJSONObject(j) ?: continue
                pts.add(
                    StoredPoint(
                        lat = p.getDouble("lat"),
                        lon = p.getDouble("lon"),
                        timeNanos = p.getLong("t"),
                        accuracyM = p.getDouble("acc"),
                        type = runCatching { WaypointType.valueOf(p.getString("type")) }
                            .getOrDefault(WaypointType.NORMAL),
                        altitudeM = optDoubleOrNull(p, "alt"),
                        speedMps = optDoubleOrNull(p, "speed"),
                        bearingDeg = optDoubleOrNull(p, "bearing"),
                    ),
                )
            }
            val media = ArrayList<BackupMedia>()
            val mArr = o.optJSONArray("media") ?: JSONArray()
            for (j in 0 until mArr.length()) {
                val m = mArr.optJSONObject(j) ?: continue
                val entry = m.optString("entry")
                if (entry.isEmpty()) continue
                media.add(
                    BackupMedia(
                        entry = entry,
                        kind = m.optString("kind", "PHOTO"),
                        lat = optDoubleOrNull(m, "lat"),
                        lon = optDoubleOrNull(m, "lon"),
                        bearingDeg = optDoubleOrNull(m, "bearing"),
                        distanceM = m.optDouble("distanceM", 0.0),
                        elapsedNanos = m.optLong("elapsedNanos", 0L),
                        createdWallMillis = m.optLong("createdWallMillis", 0L),
                        durationMs = optLongOrNull(m, "durationMs"),
                        caption = optStringOrNull(m, "caption"),
                    ),
                )
            }
            out.add(
                BackupActivity(
                    sport = o.optString("sport", "RUN"),
                    startWallMillis = o.optLong("startWallMillis", 0L),
                    distanceM = o.optDouble("distanceM", 0.0),
                    elapsedNanos = o.optLong("elapsedNanos", 0L),
                    fixCount = o.optLong("fixCount", 0L),
                    rejectedCount = o.optLong("rejectedCount", 0L),
                    mockDetected = o.optBoolean("mockDetected", false),
                    weatherTempC = optDoubleOrNull(o, "weatherTempC"),
                    weatherWindKmh = optDoubleOrNull(o, "weatherWindKmh"),
                    weatherWindDeg = optLongOrNull(o, "weatherWindDeg")?.toInt(),
                    weatherCode = optLongOrNull(o, "weatherCode")?.toInt(),
                    points = pts,
                    media = media,
                ),
            )
        }
        return BackupFile(
            version = version,
            createdWallMillis = root.optLong("created", 0L),
            activities = out,
        )
    }

    private fun putNullable(o: JSONObject, key: String, value: Any?) {
        o.put(key, value ?: JSONObject.NULL)
    }

    private fun optDoubleOrNull(o: JSONObject, key: String): Double? =
        if (o.isNull(key)) null else o.optDouble(key, Double.NaN).takeUnless { it.isNaN() }

    private fun optLongOrNull(o: JSONObject, key: String): Long? =
        if (o.isNull(key)) null else o.optLong(key)

    private fun optStringOrNull(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotEmpty() }
}
