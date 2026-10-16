package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.core.time.MonotonicTicker

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
    private var hasAcceptedFix = false
    private var lastAppendNanos = -1L

    private var fixCount = 0
    private var rejected = 0
    private var dropped = 0
    private var mocks = 0
    private var distanceM = resumeDistanceM

    val storedPoints: List<StoredPoint> get() = points.toList()
    val storedCount: Int get() = points.size
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
            store(fix, WaypointType.NORMAL, addDistance = false)
            return WaypointType.NORMAL
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
            if (heartbeatDue || idleEvent == IdleTransition.ENTERED) {
                store(fix, WaypointType.IDLE, addDistance = false)
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
        if (speed < config.idleSpeedMps) {
            val since = idleSinceNanos ?: fix.timeNanos.also { idleSinceNanos = it }
            if (!idle && ticker.secondsSince(since) >= config.idleAfterS) {
                idle = true
                return IdleTransition.ENTERED
            }
        } else {
            idleSinceNanos = null
            idle = false
        }
        return IdleTransition.NONE
    }

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
    }
}
