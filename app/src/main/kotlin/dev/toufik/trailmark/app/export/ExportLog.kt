package dev.toufik.trailmark.app.export

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** One finished export: what it was, when, and where the file ended up. */
data class ExportEntry(
    val activityId: Long,
    val title: String,
    val wallMillis: Long,
    val seconds: Int,
    val camera: String,
    val media: String,
    val uri: String,
    val bytes: Long,
)

/**
 * The last [MAX] exports, newest first, in one small JSON file.
 *
 * A render takes minutes; a user who comes back a day later needs to know
 * whether the clip they asked for exists, and where. Gallery entries are
 * remembered as content URIs, app-private ones as file paths, so the log can
 * still open what it lists.
 */
object ExportLog {
    private const val MAX = 30

    fun all(context: Context): List<ExportEntry> = runCatching {
        val file = file(context)
        if (!file.isFile) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ExportEntry(
                activityId = o.optLong("activityId"),
                title = o.optString("title"),
                wallMillis = o.optLong("wall"),
                seconds = o.optInt("seconds"),
                camera = o.optString("camera"),
                media = o.optString("media"),
                uri = o.optString("uri"),
                bytes = o.optLong("bytes"),
            )
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, entry: ExportEntry) {
        val next = (listOf(entry) + all(context).filter { it.uri != entry.uri }).take(MAX)
        val arr = JSONArray()
        next.forEach { e ->
            arr.put(
                JSONObject()
                    .put("activityId", e.activityId)
                    .put("title", e.title)
                    .put("wall", e.wallMillis)
                    .put("seconds", e.seconds)
                    .put("camera", e.camera)
                    .put("media", e.media)
                    .put("uri", e.uri)
                    .put("bytes", e.bytes),
            )
        }
        runCatching {
            val f = file(context).apply { parentFile?.mkdirs() }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, "exports/log.json")
}
