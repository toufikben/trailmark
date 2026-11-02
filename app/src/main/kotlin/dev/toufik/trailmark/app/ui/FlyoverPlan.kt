package dev.toufik.trailmark.app.ui

import kotlin.math.roundToInt

/** Frames per second of the exported clip. */
const val FLYOVER_FPS = 24

/** A long activity must not turn into a ten-thousand-frame render job. */
const val FLYOVER_MAX_FRAMES = 720

/** Short clips still need enough frames to look like motion. */
const val FLYOVER_MIN_FRAMES = 48

/** Never emit a clip shorter than one second, even for a zero-length track. */
const val FLYOVER_MIN_DURATION_NANOS = 1_000_000_000L

/**
 * How a recorded activity maps onto the exported video.
 *
 * The rule the player expects: the clip lasts `activity / speed`, so ×1 is
 * real time and ×60 is a one-minute overview of an hour. Frames are capped
 * independently, and each frame carries its own presentation time, so a long
 * activity still produces a clip of the right length without a render that
 * would never finish.
 */
data class FlyoverPlan(
    val totalNanos: Long,
    val durationNanos: Long,
    val frames: Int,
) {
    /** Activity timestamp shown at [frame]; reaches [totalNanos] on the last frame. */
    fun activityNanos(frame: Int): Long {
        if (frames <= 1) return 0L
        val at = (frame.toDouble() / (frames - 1) * totalNanos).toLong()
        return at.coerceIn(0L, totalNanos)
    }

    /** Presentation timestamp of [frame], in microseconds. */
    fun ptsUs(frame: Int): Long = frame.toLong() * durationNanos / frames / 1_000L
}

fun flyoverPlan(totalNanos: Long, speed: Int): FlyoverPlan {
    val clampedTotal = totalNanos.coerceAtLeast(0L)
    val duration = (clampedTotal / speed.coerceAtLeast(1))
        .coerceAtLeast(FLYOVER_MIN_DURATION_NANOS)
    val wanted = (duration.toDouble() / 1_000_000_000.0 * FLYOVER_FPS).roundToInt()
    val frames = wanted.coerceIn(FLYOVER_MIN_FRAMES, FLYOVER_MAX_FRAMES)
    return FlyoverPlan(clampedTotal, duration, frames)
}
