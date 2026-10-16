package dev.toufik.trailmark.recording

import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.data.ActivityRepository
import dev.toufik.trailmark.engine.FixQualityGate
import dev.toufik.trailmark.engine.FixSource
import dev.toufik.trailmark.engine.GpsQuality
import dev.toufik.trailmark.engine.RecorderEngine
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.engine.SportConfig
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Phase { STOPPED, STARTING, RECORDING }

data class RecordingState(
    val phase: Phase = Phase.STOPPED,
    val distanceM: Double = 0.0,
    val elapsedNanos: Long = 0L,
    val fixCount: Int = 0,
    val quality: GpsQuality = GpsQuality.NONE,
    val isIdle: Boolean = false,
    val storageError: String? = null,
    val lastActivityId: Long? = null,
)

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
    private val tickMs: Long = 1000,
) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private var gate: FixQualityGate? = null
    private var engine: RecorderEngine? = null
    private var activityId: Long? = null
    private var pointsWritten = 0
    private var resumeElapsedNanos = 0L
    private var resumeFrom: StoredPoint? = null
    private var resumeDistanceM = 0.0
    private var job: Job? = null
    private var tickJob: Job? = null
    private var sessionActive = false
    private val writeMutex = Mutex()

    suspend fun start() {
        if (_state.value.phase != Phase.STOPPED) return
        val resume = repo.loadResume()
        resumeElapsedNanos = resume?.elapsedNanos ?: 0L
        resumeFrom = resume?.lastStored
        resumeDistanceM = resume?.distanceM ?: 0.0
        activityId = resume?.activityId
        pointsWritten = 0
        gate = FixQualityGate(nowNanos)
        engine = null
        sessionActive = true
        _state.value = RecordingState(phase = Phase.STARTING, quality = GpsQuality.NONE)

        job = scope.launch { source.fixes().collect { onFix(it) } }
        tickJob = scope.launch {
            while (isActive) {
                delay(tickMs)
                refreshQuality()
            }
        }
    }
    private suspend fun onFix(fix: RawFix) {
        val g = gate ?: return
        val ready = g.add(fix)
        var e = engine
        if (e == null) {
            if (!ready) {
                _state.value = _state.value.copy(quality = g.quality)
                return
            }
            e = RecorderEngine(
                SportConfig.forSport(sport),
                startNanos = fix.timeNanos,
                resumeFrom = resumeFrom,
                resumeDistanceM = resumeDistanceM,
            )
            engine = e
        }
        e.onFix(fix)
        _state.value = _state.value.copy(
            phase = Phase.RECORDING,
            quality = g.quality,
            distanceM = e.distanceMeters,
            elapsedNanos = e.elapsedNanos,
            fixCount = e.stats.fixCount,
            isIdle = e.isIdle,
        )
        persist(e)
    }

    private fun refreshQuality() {
        val g = gate ?: return
        val q = g.quality
        if (q != _state.value.quality) _state.value = _state.value.copy(quality = q)
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
        job?.cancel(); job = null
        tickJob?.cancel(); tickJob = null
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
        gate = null
    }
}


