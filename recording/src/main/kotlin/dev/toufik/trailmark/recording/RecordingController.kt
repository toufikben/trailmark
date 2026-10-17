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

enum class Phase { STOPPED, STARTING, RECORDING, PAUSED }

data class RecordingState(
    val phase: Phase = Phase.STOPPED,
    val distanceM: Double = 0.0,
    val elapsedNanos: Long = 0L,
    val fixCount: Int = 0,
    val quality: GpsQuality = GpsQuality.NONE,
    val isIdle: Boolean = false,
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val currentBearing: Double? = null,
    val currentSpeedMps: Double? = null,
    val activityId: Long? = null,
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
    private val tickEnabled: Boolean = true,
) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private var gate: FixQualityGate? = null
    private var engine: RecorderEngine? = null
    private var activityId: Long? = null
    private var segmentWritten = 0
    private var resumeElapsedNanos = 0L
    private var resumeFrom: StoredPoint? = null
    private var resumeDistanceM = 0.0
    private var job: Job? = null
    private var tickJob: Job? = null
    private var sessionActive = false
    private var paused = false
    private var pausedFixCount = 0
    private var pausedRejected = 0
    private var pausedMock = false
    private val writeMutex = Mutex()

    suspend fun start() {
        if (_state.value.phase != Phase.STOPPED) return
        val resume = repo.loadResume()
        resumeElapsedNanos = resume?.elapsedNanos ?: 0L
        resumeFrom = resume?.lastStored
        resumeDistanceM = resume?.distanceM ?: 0.0
        activityId = resume?.activityId
        segmentWritten = 0
        gate = FixQualityGate(nowNanos)
        engine = null
        sessionActive = true
        paused = false
        pausedFixCount = 0
        pausedRejected = 0
        pausedMock = false
        _state.value = RecordingState(phase = Phase.STARTING, quality = GpsQuality.NONE)

        job = scope.launch { source.fixes().collect { onFix(it) } }
        if (tickEnabled) {
            tickJob = scope.launch {
                while (isActive) {
                    delay(tickMs)
                    refreshQuality()
                }
            }
        }
    }
    private suspend fun onFix(fix: RawFix) {
        val g = gate ?: return
        val ready = g.add(fix)
        if (paused) {
            _state.value = _state.value.copy(
                quality = g.quality,
                currentLat = fix.lat,
                currentLon = fix.lon,
                currentBearing = fix.bearingDeg,
                currentSpeedMps = null,
            )
            return
        }
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
            segmentWritten = 0
        }
        e.onFix(fix)
        _state.value = _state.value.copy(
            phase = Phase.RECORDING,
            quality = g.quality,
            distanceM = e.distanceMeters,
            elapsedNanos = resumeElapsedNanos + e.elapsedNanos,
            fixCount = e.stats.fixCount,
            isIdle = e.isIdle,
            currentLat = fix.lat,
            currentLon = fix.lon,
            currentBearing = fix.bearingDeg,
            currentSpeedMps = fix.speedMps,
            activityId = activityId,
        )
        persist(e)
    }

    private fun refreshQuality() {
        val g = gate ?: return
        val q = g.quality
        if (q != _state.value.quality) _state.value = _state.value.copy(quality = q)
    }
    private suspend fun persist(e: RecorderEngine) {
        if (e.storedCount <= segmentWritten) return
        writeMutex.withLock {
            val written = segmentWritten
            if (e.storedCount <= written) return@withLock
            val slice = e.storedPoints.subList(written, e.storedCount)
            try {
                val id = activityId ?: repo.insertActivity(newUuid(), sport.name, wallMillis())
                    .also { activityId = it; _state.value = _state.value.copy(activityId = it) }
                repo.appendPoints(id, slice)
                segmentWritten = written + slice.size
                repo.saveResume(id, slice.last(), e.distanceMeters, resumeElapsedNanos + e.elapsedNanos)
            } catch (t: Throwable) {
                if (sessionActive) {
                    _state.value = _state.value.copy(storageError = "storage write failed: ${t.message}")
                }
            }
        }
    }

    /**
     * Freezes time and distance: incoming fixes are dropped, the current engine is
     * retired (its totals become the base), and the checkpoint row is refreshed so a
     * crash during the pause still resumes cleanly.
     */
    suspend fun pause() {
        if (paused || _state.value.phase == Phase.STOPPED) return
        paused = true
        val e = engine
        if (e != null) {
            resumeElapsedNanos += e.elapsedNanos
            resumeDistanceM = e.distanceMeters
            pauseReportedStats(e)
            val last = e.storedPoints.lastOrNull()
            if (last != null) {
                resumeFrom = null
                val id = activityId
                if (id != null) {
                    try {
                        repo.saveResume(id, last, resumeDistanceM, resumeElapsedNanos)
                    } catch (_: Throwable) {
                        // pause state still lives in memory; the row refreshes on resume
                    }
                }
            }
            engine = null
        }
        _state.value = _state.value.copy(phase = Phase.PAUSED)
    }

    /** Continues the session in a fresh segment; the paused gap is never counted. */
    fun resume() {
        if (!paused) return
        paused = false
        _state.value = _state.value.copy(
            phase = if (activityId == null) Phase.STARTING else Phase.RECORDING,
        )
    }

    private fun pauseReportedStats(e: RecorderEngine) {
        pausedFixCount = e.stats.fixCount
        pausedRejected = e.stats.rejectedCount
        pausedMock = e.stats.mockFixCount > 0
    }

    suspend fun stop() {
        job?.cancel(); job = null
        tickJob?.cancel(); tickJob = null
        sessionActive = false
        val e = engine
        val id = activityId
        if (id != null) {
            try {
                repo.finishActivity(
                    activityId = id,
                    elapsedNanos = resumeElapsedNanos + (e?.elapsedNanos ?: 0L),
                    distanceM = e?.distanceMeters ?: resumeDistanceM,
                    fixCount = (e?.stats?.fixCount ?: pausedFixCount).toLong(),
                    rejectedCount = (e?.stats?.rejectedCount ?: pausedRejected).toLong(),
                    mockDetected = if (e != null) e.stats.mockFixCount > 0 else pausedMock,
                )
                repo.clearResume(id)
            } catch (_: Throwable) {
                // the resume row survives; the data stays recoverable
            }
        }
        _state.value = RecordingState(phase = Phase.STOPPED, lastActivityId = id)
        engine = null
        gate = null
        paused = false
    }
}


