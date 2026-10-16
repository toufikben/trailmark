package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.RawFix

/** GNSS signal states the UI reports. */
enum class GpsQuality { NONE, SEARCHING, OK, DEGRADED, LOST }

/** Accuracy tiers: > 35 m searching, 20-35 degraded, <= 20 ok. */
object GpsQualityX {
    const val goodEnoughM: Double = 35.0
    const val okOrDegradedM: Double = 20.0

    fun fromAccuracy(a: Double): GpsQuality = when {
        a > goodEnoughM -> GpsQuality.SEARCHING
        a > okOrDegradedM -> GpsQuality.DEGRADED
        else -> GpsQuality.OK
    }
}

/** ر7: a fix may open the start gate only at or below this 1σ radius. */
fun isFixGoodEnough(fix: RawFix): Boolean = fix.accuracyM <= GpsQualityX.goodEnoughM

/** An UNMEASURED 1σ maps to this worst-case sentinel; never a fake number. */
const val UNMEASURED_ACCURACY_M: Double = Double.POSITIVE_INFINITY

/** ر8: this long without any fix and the signal counts as lost. */
const val GPS_LOST_AFTER_NANOS: Long = 20L * 1_000_000_000L

/**
 * ر7/ر8 state machine. [add] returns readiness; the rule lives here and nowhere
 * else. [nowNanos] is injectable so tests advance a counter instead of sleeping.
 */
class FixQualityGate(private val nowNanos: (() -> Long)? = null) {
    var ready: Boolean = false
        private set
    private var goodStreak = 0
    private var lastAccuracyM: Double? = null
    private var lastFixAtNanos: Long? = null

    fun add(fix: RawFix): Boolean {
        lastAccuracyM = fix.accuracyM
        lastFixAtNanos = now()
        if (isFixGoodEnough(fix)) {
            goodStreak++
            if (goodStreak >= 2) ready = true
        } else {
            goodStreak = 0
        }
        return ready
    }

    val quality: GpsQuality
        get() {
            val last = lastFixAtNanos ?: return GpsQuality.NONE
            if (now() - last >= GPS_LOST_AFTER_NANOS) return GpsQuality.LOST
            return GpsQualityX.fromAccuracy(lastAccuracyM!!)
        }

    private fun now(): Long = nowNanos?.invoke() ?: System.nanoTime()
}
