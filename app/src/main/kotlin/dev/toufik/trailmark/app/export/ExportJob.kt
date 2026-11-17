package dev.toufik.trailmark.app.export

import android.content.Context
import android.net.Uri
import dev.toufik.trailmark.app.ui.FlyoverCamera
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** What to cut into the clip from the activity's captured photos and videos. */
enum class ExportMedia { OFF, PHOTOS, ALL }

/**
 * One export request, small enough to survive process death: everything else
 * (points, analysis, photos) is read back from the database when the service
 * resumes it.
 */
data class ExportJob(
    val activityId: Long,
    val seconds: Int,
    val camera: FlyoverCamera,
    val media: ExportMedia,
    val galleryCopy: Boolean,
    val dark: Boolean,
    val pois: Boolean = false,
    /** Drop onto each photo's spot and climb back out, instead of cutting straight to it. */
    val cinematic: Boolean = true,
    val lang: String,
    val sport: String,
    val title: String,
    val subtitle: String,
) {
    fun toJson(): String = JSONObject()
        .put("activityId", activityId)
        .put("seconds", seconds)
        .put("camera", camera.name)
        .put("media", media.name)
        .put("galleryCopy", galleryCopy)
        .put("dark", dark)
        .put("pois", pois)
        .put("cinematic", cinematic)
        .put("lang", lang)
        .put("sport", sport)
        .put("title", title)
        .put("subtitle", subtitle)
        .toString()

    companion object {
        fun fromJson(raw: String): ExportJob? = runCatching {
            val o = JSONObject(raw)
            ExportJob(
                activityId = o.getLong("activityId"),
                seconds = o.optInt("seconds", 0),
                camera = FlyoverCamera.entries.firstOrNull { it.name == o.optString("camera") }
                    ?: FlyoverCamera.FLY_3D,
                media = ExportMedia.entries.firstOrNull { it.name == o.optString("media") }
                    ?: ExportMedia.OFF,
                galleryCopy = o.optBoolean("galleryCopy", true),
                dark = o.optBoolean("dark", true),
                pois = o.optBoolean("pois", false),
                cinematic = o.optBoolean("cinematic", true),
                lang = o.optString("lang", "en"),
                sport = o.optString("sport", "RUN"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
            )
        }.getOrNull()
    }
}

/** Where the export is in its life. */
enum class ExportPhase { RENDERING, SAVING, DONE, FAILED, CANCELLED }

/**
 * The live export the UI shows. A single service owns it, so one value in one
 * place is enough: whoever is on screen reads it, and leaving a screen loses
 * nothing because the work does not live in a composition.
 */
data class ExportState(
    val phase: ExportPhase,
    val activityId: Long,
    val title: String,
    val progress: Float = 0f,
    val etaNanos: Long? = null,
    val uri: Uri? = null,
    val inGallery: Boolean = false,
    val error: String? = null,
)

object ExportHolder {
    private val _state = MutableStateFlow<ExportState?>(null)
    val state: StateFlow<ExportState?> = _state

    val current: ExportState? get() = _state.value

    fun publish(s: ExportState?) {
        _state.value = s
    }

    /** Called by the UI once it has shown a finished or failed export. */
    fun clear(activityId: Long) {
        if (_state.value?.activityId == activityId) _state.value = null
    }
}

/**
 * The job on disk, so a killed process can pick the export up again, plus the
 * crash counter that stops a clip which kills the renderer from looping.
 */
object ExportStore {
    private const val PREFS = "trailmark"
    private const val KEY_PENDING = "export_pending"
    private const val KEY_CRASHES = "export_crashes"

    fun savePending(context: Context, job: ExportJob) {
        // commit(): the render starts right after this, and a process death
        // before the write lands would lose the job the resume path needs.
        prefs(context).edit().putString(KEY_PENDING, job.toJson()).commit()
    }

    fun pending(context: Context): ExportJob? =
        prefs(context).getString(KEY_PENDING, null)?.let(ExportJob::fromJson)

    fun clearPending(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }

    fun crashes(context: Context): Int = prefs(context).getInt(KEY_CRASHES, 0)

    fun bumpCrashes(context: Context) {
        val p = prefs(context)
        // commit(): the process is about to die, apply() may never reach the disk.
        p.edit().putInt(KEY_CRASHES, p.getInt(KEY_CRASHES, 0) + 1).commit()
    }

    fun resetCrashes(context: Context) {
        prefs(context).edit().putInt(KEY_CRASHES, 0).apply()
    }

    /** Renders are written here and moved into the gallery when they finish. */
    fun outputDir(context: Context): File = File(context.filesDir, "exports").apply { mkdirs() }

    /**
     * Keeps app-private clips from filling storage. Gallery copies are deleted
     * as soon as they are copied, so what is left here is either still shareable
     * or the user turned the gallery copy off.
     */
    fun pruneOutputs(context: Context) {
        runCatching {
            val dir = outputDir(context)
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".mp4") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            val cutoff = System.currentTimeMillis() - KEEP_MS
            files.forEachIndexed { index, file ->
                val tooMany = index >= KEEP_FILES
                val tooOld = file.lastModified() < cutoff
                if (tooMany || tooOld) runCatching { file.delete() }
            }
        }
    }

    // The share sheet reads the finished clip through the FileProvider, so it
    // must stay readable while it sits in the app's own storage.
    fun outputFile(context: Context, activityId: Long): File =
        File(outputDir(context), "trailmark-$activityId-${System.currentTimeMillis()}.mp4")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val KEEP_FILES = 20
    private const val KEEP_MS = 7L * 24L * 60L * 60L * 1000L
}
