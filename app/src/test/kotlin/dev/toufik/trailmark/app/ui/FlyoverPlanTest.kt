package dev.toufik.trailmark.app.ui

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val S = 1_000_000_000L

/** A point [m] metres north of the start at [sec] seconds. */
private fun at(m: Double, sec: Long, type: WaypointType = WaypointType.NORMAL) =
    StoredPoint(36.0 + m / 111_195.0, 3.0, sec * S, 5.0, type)

class FlyoverPlanTest {

    @Test
    fun everyClipRunsAtThirtyFramesPerSecondWhateverTheActivityLength() {
        val short = FlyoverTimeline(totalM = 700.0, routeSeconds = 15)
        val long = FlyoverTimeline(totalM = 34_200.0, routeSeconds = 45)
        assertEquals(15 * FLYOVER_FPS, short.routeFrames)
        assertEquals(45 * FLYOVER_FPS, long.routeFrames)
        assertEquals(FLYOVER_INTRO_FRAMES + 45 * 30 + FLYOVER_OUTRO_FRAMES, long.frames)
        for (i in 1 until long.frames) {
            assertEquals(33_333L, long.ptsUs(i) - long.ptsUs(i - 1), 1.0)
        }
    }

    @Test
    fun routePartWalksTheWholeDistanceMonotonically() {
        val tl = FlyoverTimeline(totalM = 10_000.0, routeSeconds = 20)
        val first = tl.frame(FLYOVER_INTRO_FRAMES)
        val last = tl.frame(FLYOVER_INTRO_FRAMES + tl.routeFrames - 1)
        assertEquals(FlyoverPhase.ROUTE, first.phase)
        assertEquals(0.0, first.distanceM, 1e-9)
        assertEquals(10_000.0, last.distanceM, 1e-6)
        var prev = -1.0
        for (i in FLYOVER_INTRO_FRAMES until FLYOVER_INTRO_FRAMES + tl.routeFrames) {
            val d = tl.frame(i).distanceM
            assertTrue(d > prev)
            prev = d
        }
        assertEquals(FlyoverPhase.INTRO, tl.frame(0).phase)
        assertEquals(FlyoverPhase.OUTRO, tl.frame(tl.frames - 1).phase)
        assertEquals(1.0, tl.frame(tl.frames - 1).t, 1e-9)
    }

    @Test
    fun pausesTakeNoScreenTimeAndNoMovingTime() {
        // 100 m in 50 s, a 50-minute stop in place, then 100 m more in 50 s.
        val path = FlyoverPath(
            listOf(
                at(0.0, 0),
                at(100.0, 50),
                at(100.0, 60, WaypointType.IDLE),
                at(100.0, 3_050),
                at(200.0, 3_100),
            ),
        )
        assertEquals(200.0, path.totalM, 0.5)
        assertEquals(100 * S, path.movingNanos)
        // Halfway along the distance is halfway through the clip, not after the stop.
        assertEquals(50 * S, path.movingNanosAt(100.0), (S / 100).toDouble())
        assertEquals(2.0, path.speedAt(50.0), 0.05)
    }

    @Test
    fun samplesInterpolateAndBearingLooksAhead() {
        val path = FlyoverPath(listOf(at(0.0, 0), at(1_000.0, 500)))
        assertEquals(36.0 + 500.0 / 111_195.0, path.at(500.0).lat, 1e-7)
        assertEquals(0.0, path.bearingAt(100.0, 80.0), 0.5)
        // Near the end the look-ahead window slides back instead of collapsing.
        assertEquals(0.0, path.bearingAt(999.0, 80.0), 0.5)
        val pre = path.prefix(250.0)
        assertEquals(2, pre.size)
        assertEquals(path.at(250.0).lat, pre.last().lat, 1e-12)
    }

    @Test
    fun rejectedSpikesAreIgnored() {
        val path = FlyoverPath(
            listOf(at(0.0, 0), at(50_000.0, 1, WaypointType.REJECTED), at(100.0, 50)),
        )
        assertEquals(100.0, path.totalM, 0.5)
    }

    @Test
    fun autoLengthAndZoomStayInRange() {
        assertEquals(15, autoFlyoverSeconds(700.0))
        assertEquals(22, autoFlyoverSeconds(10_000.0))
        assertEquals(45, autoFlyoverSeconds(120_000.0))
        assertTrue(followZoom(1.5, tilted = true) > followZoom(15.0, tilted = true))
        assertTrue(followZoom(3.0, tilted = false) > followZoom(3.0, tilted = true))
    }

    @Test
    fun easingStartsAndEndsInPlace() {
        assertEquals(0.0, routeEase(0.0), 1e-12)
        assertEquals(1.0, routeEase(1.0), 1e-12)
        assertEquals(0.5, easeInOut(0.5), 1e-12)
    }
}
