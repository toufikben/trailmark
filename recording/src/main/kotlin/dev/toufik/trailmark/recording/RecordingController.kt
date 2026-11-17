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
import kotlinx.coroutines.flow.update
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
    val autoPaused: Boolean = false,
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

    // Written by the pause/resume coroutines and read by the fix collector, so
    // the flags and the engine handle cross threads on purpose.
    @Volatile
    private var gate: FixQualityGate? = null

    @Volatile
    private var engine: RecorderEngine? = null

    @Volatile
    private var activityId: Long? = null
    private var segmentWritten = 0
    private var resumeElapsedNanos = 0L
    private var resumeFrom: StoredPoint? = null
    private var resumeDistanceM = 0.0
    private var job: Job? = null
    private var tickJob: Job? = null
    private var sessionActive = false

    @Volatile
    private var paused = false
    private var pausedFixCount = 0
    private var pausedRejected = 0
    private var pausedMock = false
    private var cumFixCount = 0
    private var cumRejected = 0
    private var cumMock = false
    private var autoPause = false

    @Volatile
    private var autoPaused = false
    private val writeMutex = Mutex()

    /** Enables or disables the idle auto-pause. Disabling lifts an auto-pause at once. */
    fun setAutoPause(enabled: Boolean) {
        autoPause = enabled
        if (!enabled && autoPaused) resume()
    }

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
        cumFixCount = 0
        cumRejected = 0
        cumMock = false
        autoPaused = false
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
            _state.update {
                it.copy(
                    quality = g.quality,
                    currentLat = fix.lat,
                    currentLon = fix.lon,
                    currentBearing = fix.bearingDeg,
                    currentSpeedMps = null,
                )
            }
            if (autoPaused) {
                val s = fix.speedMps
                if (s != null && s > 0.8) {
                    autoPaused = false
                    resume()
                }
            }
            return
        }
        var e = engine
        if (e == null) {
            if (!ready) {
                _state.update { it.copy(quality = g.quality) }
                return
            }
            e = RecorderEngine(
                SportConfig.forSport(sport),
                startNanos = fix.timeNanos,
                resumeFrom = resumeFrom,
                resumeDistanceM = resumeDistanceM,
                // Any engine that is not the first one of the session starts a
                // new segment: the leg between the previous segment's last point
                // and this one spans a pause (or a killed process) and was never
                // travelled. Splits and moving time must not bill it.
                startsNewSegment = resumeFrom != null ||
                    resumeDistanceM > 0.0 ||
                    resumeElapsedNanos > 0L,
            )
            engine = e
            segmentWritten = 0
        }
        e.onFix(fix)
        _state.update {
            it.copy(
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
        }
        persist(e)
        if (autoPause && !paused && e.isIdle) {
            autoPaused = true
            pause()
        }
    }

    private fun refreshQuality() {
        val g = gate ?: return
        val q = g.quality
        // Atomic read-modify-write: the tick and the fix collector run on
        // different threads, and a plain copy from a stale snapshot used to put
        // the previous distance back on screen for a second.
        _state.update { if (it.quality == q) it else it.copy(quality = q) }
    }
    private suspend fun persist(e: RecorderEngine) {
        if (!sessionActive) return
        if (e.storedCount <= segmentWritten) return
        writeMutex.withLock {
            val written = segmentWritten
            if (!sessionActive) return@withLock
            if (e.storedCount <= written) return@withLock
            val slice = e.storedSlice(written, e.storedCount)
            try {
                val id = activityId ?: repo.insertActivity(newUuid(), sport.name, wallMillis())
                    .also { activityId = it; _state.update { s -> s.copy(activityId = it) } }
                repo.appendPoints(id, slice)
                segmentWritten = written + slice.size
                repo.saveResume(id, slice.last(), e.distanceMeters, resumeElapsedNanos + e.elapsedNanos)
            } catch (t: Throwable) {
                if (sessionActive) {
                    _state.update { it.copy(storageError = "storage write failed: ${t.message}") }
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
        _state.update { it.copy(phase = Phase.PAUSED, autoPaused = autoPaused) }
    }

    /** Continues the session in a fresh segment; the paused gap is never counted. */
    fun resume() {
        if (!paused) return
        paused = false
        autoPaused = false
        _state.update {
            it.copy(
                phase = if (activityId == null) Phase.STARTING else Phase.RECORDING,
                autoPaused = false,
            )
        }
    }

    private fun pauseReportedStats(e: RecorderEngine) {
        cumFixCount += e.stats.fixCount
        cumRejected += e.stats.rejectedCount
        cumMock = cumMock || e.stats.mockFixCount > 0
        pausedFixCount = e.stats.fixCount
        pausedRejected = e.stats.rejectedCount
        pausedMock = e.stats.mockFixCount > 0
    }

    suspend fun stop() {
        job?.cancel()
        job?.join()
        job = null
        tickJob?.cancel()
        tickJob?.join()
        tickJob = null
        sessionActive = false
        val e = engine
        val id = activityId
        // The row must never be finished twice. A second stop — the notification
        // action racing the in-app button, or a double tap — arrives with no
        // engine and would rewrite the just-saved effort as 0 m / 0 s.
        activityId = null
        if (id != null) {
            try {
                repo.finishActivity(
                    activityId = id,
                    elapsedNanos = resumeElapsedNanos + (e?.elapsedNanos ?: 0L),
                    distanceM = e?.distanceMeters ?: resumeDistanceM,
                    fixCount = (cumFixCount + (e?.stats?.fixCount ?: 0)).toLong(),
                    rejectedCount = (cumRejected + (e?.stats?.rejectedCount ?: 0)).toLong(),
                    mockDetected = cumMock || (e?.stats?.mockFixCount ?: 0) > 0,
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
        autoPaused = false
    }
}


