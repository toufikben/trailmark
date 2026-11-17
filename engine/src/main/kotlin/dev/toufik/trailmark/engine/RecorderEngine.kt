package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.geo.bearingDeg
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.core.time.MonotonicTicker
import kotlin.math.abs

/** Sink for EVERY accepted raw fix, invoked synchronously before onFix returns. */
fun interface RawFixSink {
    fun onVerdict(fix: RawFix, verdict: WaypointType)
}

/** Counters exposed to the UI/persistence layers. */
data class EngineStats(
    val fixCount: Int = 0,
    val rejectedCount: Int = 0,
    val droppedOutOfOrder: Int = 0,
    val mockFixCount: Int = 0,
)

private enum class IdleTransition { NONE, ENTERED }

/**
 * Pure recorder core. Consumes RawFixes, accumulates distance from the anchor
 * (the last point that PASSED the jump test), rejects impossible jumps without
 * deleting them, re-acquires the anchor after genuine transport, and marks
 * standing-still as IDLE while time keeps flowing.
 */
class RecorderEngine(
    val config: SportConfig,
    startNanos: Long,
    private val rawSink: RawFixSink? = null,
    resumeFrom: StoredPoint? = null,
    resumeDistanceM: Double = 0.0,
    /**
     * True when this engine starts the first segment after a pause. The first
     * stored point is then flagged RESUME, so every consumer of the stored
     * track knows the leg from the previous segment was never walked.
     */
    private val startsNewSegment: Boolean = false,
) {
    private val ticker = MonotonicTicker()
    private val sessionStartNanos = startNanos
    private val points = ArrayList<StoredPoint>()

    private var anchor: StoredPoint? = resumeFrom
    private var chainPrev: StoredPoint? = null
    private var consistentPairs = 0
    private var chainDistanceM = 0.0

    private var idleSinceNanos: Long? = null
    private var idle = false

    // Candidate move after standing still: the last exploratory fix, how many
    // consistent steps it has, and how far those steps walked.
    private var idleStepLat = Double.NaN
    private var idleStepLon = 0.0
    private var idleStepNanos = 0L
    private var idleStepBearing = Double.NaN
    private var idleStepPairs = 0
    private var idleStepDistanceM = 0.0
    private var hasAcceptedFix = false
    private var lastAppendNanos = -1L

    private var fixCount = 0
    private var rejected = 0
    private var dropped = 0
    private var mocks = 0
    private var distanceM = resumeDistanceM

    val storedPoints: List<StoredPoint> get() = points.toList()
    val storedCount: Int get() = points.size

    /** Copy of `[from, to)` without materializing the whole track. */
    fun storedSlice(from: Int, to: Int): List<StoredPoint> = points.subList(from, to).toList()
    val distanceMeters: Double get() = distanceM
    val isIdle: Boolean get() = idle
    val stats: EngineStats
        get() = EngineStats(fixCount, rejected, dropped, mocks)

    /** 0 until the first fix is accepted, then ticker-last minus start, never negative. */
    val elapsedNanos: Long
        get() {
            if (!hasAcceptedFix) return 0L
            val e = ticker.lastNanos - sessionStartNanos
            return if (e < 0) 0L else e
        }

    fun onFix(fix: RawFix) {
        if (!ticker.accept(fix.timeNanos)) {
            dropped++
            return
        }
        hasAcceptedFix = true
        fixCount++
        if (fix.isMocked) mocks++
        val verdict = process(fix)
        rawSink?.onVerdict(fix, verdict)
    }

    private fun process(fix: RawFix): WaypointType {
        val a = anchor
        if (a == null) {
            val first = if (startsNewSegment) WaypointType.RESUME else WaypointType.NORMAL
            store(fix, first, addDistance = false)
            return first
        }

        val dt = ticker.secondsSince(a.timeNanos)
        val d = haversineMeters(a.lat, a.lon, fix.lat, fix.lon)
        val gateM = config.gateFor(fix.accuracyM)
        val impliedSpeed = if (dt > 0) d / dt else null
        val speed = fix.speedMps ?: impliedSpeed

        if (d > config.maxJumpM ||
            (impliedSpeed != null && impliedSpeed > config.maxSpeedMps)
        ) {
            rejected++
            trackReacquisition(store(fix, WaypointType.REJECTED, addDistance = false))
            return WaypointType.REJECTED
        }
        resetChain()

        val idleEvent = updateIdle(fix, speed ?: 0.0)
        val heartbeatDue = lastAppendNanos >= 0 &&
            ticker.secondsSince(lastAppendNanos) >= config.forceStoreAfterS

        if (idle) {
            // A device can report a measured speed of zero while the runner is
            // in fact moving, so the gate alone cannot end the idle. But a lone
            // jump over the gate is far more often the GPS wandering around a
            // standing phone, which used to bill kilometres over a long sit.
            // Movement is believed only when several consecutive fixes agree on
            // one heading and their net displacement is most of the distance
            // walked; the random walk of standing drift fails both tests.
            if (d >= gateM) {
                val measured = (fix.speedMps ?: 0.0) >= config.idleSpeedMps
                val needed = if (measured) IDLE_MOVING_PAIRS else IDLE_STANDING_PAIRS
                if (idleMovementConfirmed(fix, a, needed)) {
                    idle = false
                    idleSinceNanos = null
                    resetIdleChain()
                    store(fix, WaypointType.NORMAL, addDistance = true)
                    return WaypointType.NORMAL
                }
            }
            if (heartbeatDue || idleEvent == IdleTransition.ENTERED) {
                // The anchor stays on the last trusted position while the phone
                // stands, or every heartbeat would forgive the drift and the
                // next jump would be billed from a fresh, wrong anchor.
                store(fix, WaypointType.IDLE, addDistance = false, moveAnchor = false)
            }
            return WaypointType.IDLE
        }
        if (d >= gateM) {
            store(fix, WaypointType.NORMAL, addDistance = true)
            return WaypointType.NORMAL
        }
        if (heartbeatDue) {
            store(fix, WaypointType.IDLE, addDistance = false, moveAnchor = false)
            return WaypointType.IDLE
        }
        return WaypointType.SKIPPED
    }

    private fun updateIdle(fix: RawFix, speed: Double): IdleTransition {
        // Only entering is decided here. Leaving is the movement chain's job:
        // clearing idle on a reported walk speed reopened drift billing, since
        // indoor GPS reports a small non-zero speed while standing.
        if (idle) return IdleTransition.NONE
        if (speed < config.idleSpeedMps) {
            val since = idleSinceNanos ?: fix.timeNanos.also { idleSinceNanos = it }
            if (ticker.secondsSince(since) >= config.idleAfterS) {
                idle = true
                return IdleTransition.ENTERED
            }
        } else {
            idleSinceNanos = null
        }
        return IdleTransition.NONE
    }

    /**
     * True once the fixes since the idle began look like a real move: [needed]
     * consecutive steps in one heading, with a net displacement that is most of
     * the distance walked. Standing drift crosses the gate again and again but
     * never holds a heading, and its random walk makes the net much shorter
     * than the path.
     */
    private fun idleMovementConfirmed(fix: RawFix, anchor: StoredPoint, needed: Int): Boolean {
        if (idleStepLat.isNaN()) {
            startIdleChain(fix)
            return false
        }
        val dt = ticker.secondsSince(idleStepNanos)
        val step = haversineMeters(idleStepLat, idleStepLon, fix.lat, fix.lon)
        if (dt <= 0 || step / dt > config.maxSpeedMps) {
            startIdleChain(fix)
            return false
        }
        val bearing = bearingDeg(idleStepLat, idleStepLon, fix.lat, fix.lon)
        if (!idleStepBearing.isNaN() &&
            abs(angleBetween(bearing, idleStepBearing)) > IDLE_BEARING_TOL_DEG
        ) {
            // Another heading is a new candidate, not the same move.
            startIdleChain(fix)
            return false
        }
        idleStepBearing = bearing
        idleStepDistanceM += step
        idleStepPairs++
        idleStepLat = fix.lat
        idleStepLon = fix.lon
        idleStepNanos = fix.timeNanos
        if (idleStepPairs < needed) return false
        val net = haversineMeters(anchor.lat, anchor.lon, fix.lat, fix.lon)
        return net >= idleStepDistanceM * IDLE_STRAIGHTNESS
    }

    private fun startIdleChain(fix: RawFix) {
        idleStepLat = fix.lat
        idleStepLon = fix.lon
        idleStepNanos = fix.timeNanos
        idleStepBearing = Double.NaN
        idleStepPairs = 0
        idleStepDistanceM = 0.0
    }

    private fun resetIdleChain() {
        idleStepLat = Double.NaN
        idleStepLon = 0.0
        idleStepNanos = 0L
        idleStepBearing = Double.NaN
        idleStepPairs = 0
        idleStepDistanceM = 0.0
    }

    private fun angleBetween(a: Double, b: Double): Double = ((a - b + 540.0) % 360.0) - 180.0

    private fun trackReacquisition(p: StoredPoint) {
        val prev = chainPrev
        if (prev != null) {
            val dt = ticker.secondsSince(prev.timeNanos)
            val d = haversineMeters(prev.lat, prev.lon, p.lat, p.lon)
            if (dt > 0 && d / dt <= config.maxSpeedMps) {
                consistentPairs++
                chainDistanceM += d
                if (consistentPairs >= REANCHOR_AFTER_PAIRS) {
                    anchor = p
                    distanceM += chainDistanceM
                    resetChain()
                    return
                }
                chainPrev = p
                return
            }
        }
        chainPrev = p
        consistentPairs = 0
        chainDistanceM = 0.0
    }

    private fun resetChain() {
        chainPrev = null
        consistentPairs = 0
        chainDistanceM = 0.0
    }

    private fun store(
        fix: RawFix,
        type: WaypointType,
        addDistance: Boolean,
        moveAnchor: Boolean = true,
    ): StoredPoint {
        val a = anchor
        if (addDistance && a != null) {
            distanceM += haversineMeters(a.lat, a.lon, fix.lat, fix.lon)
        }
        val p = StoredPoint(
            lat = fix.lat,
            lon = fix.lon,
            timeNanos = fix.timeNanos,
            accuracyM = fix.accuracyM,
            type = type,
            altitudeM = fix.altitudeM,
            speedMps = fix.speedMps,
            bearingDeg = fix.bearingDeg,
        )
        points.add(p)
        lastAppendNanos = fix.timeNanos
        if (type != WaypointType.REJECTED && moveAnchor) anchor = p
        return p
    }

    companion object {
        /** R40: rejected fixes must be mutually consistent across this many pairs. */
        const val REANCHOR_AFTER_PAIRS = 3

        /** Consistent steps that prove movement when no walk speed is reported. */
        private const val IDLE_STANDING_PAIRS = 3

        /** ... and when the device itself reports a walking speed. */
        private const val IDLE_MOVING_PAIRS = 2

        /** How far a step may turn before it counts as a different move. */
        private const val IDLE_BEARING_TOL_DEG = 55.0

        /** Net displacement must be at least this share of the distance walked. */
        private const val IDLE_STRAIGHTNESS = 0.6
    }
}
