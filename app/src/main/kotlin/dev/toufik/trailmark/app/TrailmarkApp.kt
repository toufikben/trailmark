package dev.toufik.trailmark.app

import android.app.Application
import dev.toufik.trailmark.app.export.ExportHolder
import dev.toufik.trailmark.app.export.ExportPhase
import dev.toufik.trailmark.app.export.ExportStore
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.service.RecordingService
import dev.toufik.trailmark.recording.Phase

/**
 * Counts crashes that happen while a run is being recorded or a clip rendered.
 *
 * Both jobs live in the same process as the UI, so a crash in either one kills
 * everything. The checkpoint on disk is what lets the services resume after the
 * system restarts them (START_STICKY); these counters only stop a crash loop
 * from restarting the same broken session forever. `commit()` is deliberate: an
 * `apply()` may never reach the disk when the process is about to die.
 */
class TrailmarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The export service can be restarted by the system without any
        // activity, and MapLibre must already be initialised then.
        org.maplibre.android.MapLibre.getInstance(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val phase = RecordingHolder.state.value.phase
                if (phase == Phase.RECORDING || phase == Phase.PAUSED) {
                    val prefs = getSharedPreferences("trailmark", MODE_PRIVATE)
                    prefs.edit()
                        .putInt(
                            RecordingService.KEY_CRASHES,
                            prefs.getInt(RecordingService.KEY_CRASHES, 0) + 1,
                        )
                        .commit()
                }
                val export = ExportHolder.current?.phase
                if (export == ExportPhase.RENDERING || export == ExportPhase.SAVING) {
                    ExportStore.bumpCrashes(this)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
