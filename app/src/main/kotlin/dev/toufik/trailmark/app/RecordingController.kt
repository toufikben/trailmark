package dev.toufik.trailmark.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.toufik.trailmark.engine.RecorderEngine
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.engine.SportConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RecordingUiState(
    val running: Boolean = false,
    val distanceM: Double = 0.0,
    val elapsedNanos: Long = 0L,
    val fixCount: Int = 0,
)

/** Minimal Plan-04 controller: synthetic source → engine → UI state. */
class RecordingController(private val source: SyntheticFixSource = SyntheticFixSource()) {
    var state by mutableStateOf(RecordingUiState())
        private set

    private var engine: RecorderEngine? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (state.running) return
        val start = System.nanoTime()
        val e = RecorderEngine(SportConfig.forSport(Sport.RUN), start)
        engine = e
        state = RecordingUiState(running = true)
        job = scope.launch {
            source.fixes(start).collect { fix ->
                e.onFix(fix)
                state = RecordingUiState(
                    running = true,
                    distanceM = e.distanceMeters,
                    elapsedNanos = e.elapsedNanos,
                    fixCount = e.stats.fixCount,
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        state = state.copy(running = false)
    }
}
