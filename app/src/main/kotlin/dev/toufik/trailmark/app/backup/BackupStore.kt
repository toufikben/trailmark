package dev.toufik.trailmark.app.backup

import android.content.Context
import android.net.Uri
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.MediaCapture
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Writes and reads the backup archive: one `backup.json` entry describing every
 * finished activity, plus the captured photos and clips under `media/`.
 *
 * Only finished activities are backed up — a session that is still running has
 * no final numbers, and its checkpoint is meaningless on another install.
 */
object BackupStore {

    const val JSON_ENTRY = "backup.json"

    /** Writes the archive to a document the user picked. Returns activity count. */
    fun write(context: Context, uri: Uri): Int {
        val repo = AppGraph.repository(context)
        val rows = repo.listActivities().filterNot { it.isOpen }
        val activities = ArrayList<BackupActivity>(rows.size)
        val files = LinkedHashMap<String, File>()

        for (row in rows) {
            val points = repo.loadPoints(row.id)
            val media = repo.listMedia(row.id).mapNotNull { m ->
                val file = File(m.filePath)
                if (!file.exists()) return@mapNotNull null
                val ext = if (m.kind == "VIDEO") "mp4" else "jpg"
                val entry = "media/${row.id}-${m.id}.$ext"
                files[entry] = file
                BackupMedia(
                    entry = entry,
                    kind = m.kind,
                    lat = m.lat,
                    lon = m.lon,
                    bearingDeg = m.bearingDeg,
                    distanceM = m.distanceM,
                    elapsedNanos = m.elapsedNanos,
                    createdWallMillis = m.createdWallMillis,
                    durationMs = m.durationMs,
                    caption = m.caption,
                )
            }
            activities.add(
                BackupActivity(
                    sport = row.sport,
                    startWallMillis = row.startWallMillis,
                    distanceM = row.distanceM,
                    elapsedNanos = row.elapsedNanos,
                    fixCount = row.fixCount,
                    rejectedCount = row.rejectedCount,
                    mockDetected = row.mockDetected,
                    weatherTempC = row.weatherTempC,
                    weatherWindKmh = row.weatherWindKmh,
                    weatherWindDeg = row.weatherWindDeg?.toInt(),
                    weatherCode = row.weatherCode?.toInt(),
                    points = points,
                    media = media,
                ),
            )
        }

        val json = BackupCodec.encode(
            BackupFile(
                createdWallMillis = System.currentTimeMillis(),
                activities = activities,
            ),
        )

        val out = context.contentResolver.openOutputStream(uri) ?: return 0
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((entry, file) in files) {
                zip.putNextEntry(ZipEntry(entry))
                BufferedInputStream(file.inputStream()).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return activities.size
    }

    /**
     * Restores an archive into the database and the app's media folder. Media
     * rows whose bytes are missing from the archive are skipped rather than
     * pointing at a file that will never exist.
     */
    fun restore(context: Context, uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri) ?: return -1
        var json: String? = null
        val mediaBytes = HashMap<String, ByteArray>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == JSON_ENTRY -> {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry.name.startsWith("media/") -> mediaBytes[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val backup = json?.let { BackupCodec.decode(it) } ?: return -1
        val repo = AppGraph.repository(context)
        var restored = 0
        for (a in backup.activities) {
            val id = runCatching {
                val newId = repo.insertActivity(
                    uuid = "backup-${System.currentTimeMillis()}-${restored}",
                    sport = a.sport,
                    startWallMillis = a.startWallMillis,
                )
                if (a.points.isNotEmpty()) repo.appendPoints(newId, a.points)
                repo.finishActivity(
                    activityId = newId,
                    elapsedNanos = a.elapsedNanos,
                    distanceM = a.distanceM,
                    fixCount = a.fixCount,
                    rejectedCount = a.rejectedCount,
                    mockDetected = a.mockDetected,
                )
                val temp = a.weatherTempC
                val wind = a.weatherWindKmh
                val deg = a.weatherWindDeg
                val code = a.weatherCode
                if (temp != null && wind != null && deg != null && code != null) {
                    repo.setWeather(newId, temp, wind, deg, code)
                }
                for (m in a.media) {
                    val bytes = mediaBytes[m.entry] ?: continue
                    val file = if (m.kind == "VIDEO") {
                        MediaCapture.newVideoFile(context)
                    } else {
                        MediaCapture.newPhotoFile(context)
                    }
                    runCatching { file.outputStream().use { it.write(bytes) } }
                        .getOrNull()
                        ?.let {
                            repo.insertMedia(
                                activityId = newId,
                                kind = m.kind,
                                filePath = file.absolutePath,
                                lat = m.lat,
                                lon = m.lon,
                                bearingDeg = m.bearingDeg,
                                distanceM = m.distanceM,
                                elapsedNanos = m.elapsedNanos,
                                createdWallMillis = m.createdWallMillis,
                                durationMs = m.durationMs,
                                caption = m.caption,
                            )
                        }
                }
                newId
            }.getOrNull() ?: continue
            if (id > 0) restored++
        }
        return restored
    }
}
