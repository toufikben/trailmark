package dev.toufik.trailmark.recording

import dev.toufik.trailmark.data.ActivityRepository
import dev.toufik.trailmark.engine.FixSource
import dev.toufik.trailmark.engine.RecorderEngine
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.engine.SportConfig
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Phase { STOPPED, RECORDING }

data class RecordingState(
    val phase: Phase = Phase.STOPPED,
    val distanceM: Double = 0.0,
    val elapsedNanos: Long = 0L,
    val fixCount: Int = 0,
    val storageError: String? = null,
    val lastActivityId: Long? = null,
)

/**
 * Wires a [FixSource] through the engine into the repository: every stored point
 * is written immediately (serialized), a resume checkpoint is kept current, and
 * stop() finalizes the row. Distance carries across a resume; on-screen time
 * restarts; the persisted elapsed accumulates.
 */
class RecordingController(
    private val repo: ActivityRepository,
    private val source: FixSource,
    private val scope: CoroutineScope,
    private val sport: Sport = Sport.RUN,
    private val nowNanos: () -> Long = { System.nanoTime() },
    private val wallMillis: () -> Long = { System.currentTimeMillis() },
    private val newUuid: () -> String = {
        "act-${System.currentTimeMillis()}-${Random.nextInt(1 shl 30)}"
    },
) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private var engine: RecorderEngine? = null
    private var activityId: Long? = null
    private var pointsWritten = 0
    private var resumeElapsedNanos = 0L
    private var job: Job? = null
    private var sessionActive = false
    private val writeMutex = Mutex()

    suspend fun start() {
        if (_state.value.phase != Phase.STOPPED) return
        val resume = repo.loadResume()
        resumeElapsedNanos = resume?.elapsedNanos ?: 0L
        val e = RecorderEngine(
            SportConfig.forSport(sport),
            startNanos = nowNanos(),
            resumeFrom = resume?.lastStored,
            resumeDistanceM = resume?.distanceM ?: 0.0,
        )
        engine = e
        activityId = resume?.activityId
        pointsWritten = 0
        sessionActive = true
        _state.value = RecordingState(phase = Phase.RECORDING)

        job = scope.launch {
            source.fixes().collect { fix ->
                e.onFix(fix)
                _state.value = _state.value.copy(
                    distanceM = e.distanceMeters,
                    elapsedNanos = e.elapsedNanos,
                    fixCount = e.stats.fixCount,
                )
                persist(e)
            }
        }
    }

    private suspend fun persist(e: RecorderEngine) {
        if (e.storedCount <= pointsWritten) return
        writeMutex.withLock {
            val written = pointsWritten
            if (e.storedCount <= written) return@withLock
            val slice = e.storedPoints.subList(written, e.storedCount)
            try {
                val id = activityId ?: repo.insertActivity(newUuid(), sport.name, wallMillis())
                    .also { activityId = it }
                repo.appendPoints(id, slice)
                pointsWritten = written + slice.size
                repo.saveResume(id, slice.last(), e.distanceMeters, resumeElapsedNanos + e.elapsedNanos)
            } catch (t: Throwable) {
                if (sessionActive) {
                    _state.value = _state.value.copy(storageError = "storage write failed: ${t.message}")
                }
            }
        }
    }

    suspend fun stop() {
        job?.cancel()
        job = null
        sessionActive = false
        val e = engine
        val id = activityId
        if (e != null && id != null) {
            try {
                repo.finishActivity(
                    activityId = id,
                    elapsedNanos = resumeElapsedNanos + e.elapsedNanos,
                    distanceM = e.distanceMeters,
                    fixCount = e.stats.fixCount.toLong(),
                    rejectedCount = e.stats.rejectedCount.toLong(),
                    mockDetected = e.stats.mockFixCount > 0,
                )
                repo.clearResume(id)
            } catch (_: Throwable) {
                // the resume row survives; the data stays recoverable
            }
        }
        _state.value = RecordingState(phase = Phase.STOPPED, lastActivityId = id)
        engine = null
    }
}
