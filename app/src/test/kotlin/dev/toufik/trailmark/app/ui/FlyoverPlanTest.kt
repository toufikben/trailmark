package dev.toufik.trailmark.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlyoverPlanTest {

    @Test
    fun clipLastsActivityDurationDividedBySpeed() {
        val eightMinutes = 8L * 60 * 1_000_000_000 + 27 * 1_000_000_000

        assertEquals(eightMinutes, flyoverPlan(eightMinutes, 1).durationNanos)
        assertEquals(eightMinutes / 4, flyoverPlan(eightMinutes, 4).durationNanos)
        assertEquals(eightMinutes / 60, flyoverPlan(eightMinutes, 60).durationNanos)
    }

    @Test
    fun speedIsNeverTreatedAsZero() {
        val total = 90_000_000_000L
        assertEquals(flyoverPlan(total, 1), flyoverPlan(total, 0))
        assertEquals(flyoverPlan(total, 1), flyoverPlan(total, -5))
    }

    @Test
    fun longActivitiesKeepTheirLengthWithACappedFrameCount() {
        val eightMinutes = 8L * 60 * 1_000_000_000 + 27 * 1_000_000_000
        val plan = flyoverPlan(eightMinutes, 1)

        assertEquals(FLYOVER_MAX_FRAMES, plan.frames)
        assertEquals(eightMinutes, plan.durationNanos)
    }

    @Test
    fun shortClipsStillGetEnoughFrames() {
        val plan = flyoverPlan(500_000_000L, 1)

        assertEquals(FLYOVER_MIN_DURATION_NANOS, plan.durationNanos)
        assertEquals(FLYOVER_MIN_FRAMES, plan.frames)
    }

    @Test
    fun normalClipsRunAtTheTargetFrameRate() {
        val sixSeconds = 6_000_000_000L
        val plan = flyoverPlan(sixSeconds, 1)

        assertEquals(6 * FLYOVER_FPS, plan.frames)
        assertEquals(sixSeconds, plan.durationNanos)
    }

    @Test
    fun activitySamplesWalkFromStartToFinish() {
        val total = 8L * 60 * 1_000_000_000 + 27 * 1_000_000_000
        val plan = flyoverPlan(total, 4)

        assertEquals(0L, plan.activityNanos(0))
        assertEquals(total, plan.activityNanos(plan.frames - 1))
        assertTrue(plan.activityNanos(plan.frames / 2) > 0L)
        assertTrue(plan.activityNanos(plan.frames / 2) < total)
    }

    @Test
    fun frameTimestampsAreMonotonicAndSpanTheClip() {
        val total = 8L * 60 * 1_000_000_000 + 27 * 1_000_000_000
        val plan = flyoverPlan(total, 16)

        assertEquals(0L, plan.ptsUs(0))
        for (frame in 1 until plan.frames) {
            assertTrue(plan.ptsUs(frame) > plan.ptsUs(frame - 1))
        }
        val lastPts = plan.ptsUs(plan.frames - 1)
        val durationUs = plan.durationNanos / 1_000L
        // The final frame starts one frame before the end, so the clip ends on time.
        assertTrue(durationUs - lastPts <= durationUs / plan.frames + 1)
    }

    @Test
    fun rejectedOrEmptyTrackStillProducesAValidPlan() {
        val plan = flyoverPlan(0L, 1)

        assertEquals(FLYOVER_MIN_DURATION_NANOS, plan.durationNanos)
        assertEquals(FLYOVER_MIN_FRAMES, plan.frames)
        assertEquals(0L, plan.activityNanos(0))
        assertEquals(0L, plan.activityNanos(plan.frames - 1))
    }
}
