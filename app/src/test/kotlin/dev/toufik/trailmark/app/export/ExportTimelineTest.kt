package dev.toufik.trailmark.app.export

import dev.toufik.trailmark.app.ui.FLYOVER_FPS
import dev.toufik.trailmark.app.ui.FLYOVER_INTRO_FRAMES
import dev.toufik.trailmark.app.ui.FLYOVER_OUTRO_FRAMES
import dev.toufik.trailmark.app.ui.FlyoverPath
import dev.toufik.trailmark.app.ui.FlyoverPhase
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A point [m] metres north of the start at [sec] seconds. */
private fun at(m: Double, sec: Long, type: WaypointType = WaypointType.NORMAL) =
    StoredPoint(36.0 + m / 111_195.0, 3.0, sec * 1_000_000_000L, 5.0, type)

class ExportTimelineTest {

    private fun path(): FlyoverPath = FlyoverPath(
        listOf(
            at(0.0, 0),
            at(2_000.0, 600),
            at(4_000.0, 1_200),
        ),
    )

    @Test
    fun aRouteWithoutMediaStillRendersTheWholeFlight() {
        val p = path()
        val timeline = ExportTimeline(p, routeSeconds = 15, clips = emptyList())
        assertEquals(
            FLYOVER_INTRO_FRAMES + 15 * FLYOVER_FPS + FLYOVER_OUTRO_FRAMES,
            timeline.frames,
        )
        val first = timeline.frame(FLYOVER_INTRO_FRAMES) as ExportFrame.Map
        assertEquals(FlyoverPhase.ROUTE, first.phase)
        assertEquals(0.0, first.distanceM, 0.01)
        val last = timeline.frame(timeline.frames - 1) as ExportFrame.Map
        assertEquals(FlyoverPhase.OUTRO, last.phase)
        assertEquals(p.totalM, last.distanceM, 0.01)
    }

    @Test
    fun theRoutePartAdvancesToTheEnd() {
        val p = path()
        val timeline = ExportTimeline(p, routeSeconds = 15, clips = emptyList())
        var previous = -1.0
        for (i in FLYOVER_INTRO_FRAMES until FLYOVER_INTRO_FRAMES + 15 * FLYOVER_FPS) {
            val d = (timeline.frame(i) as ExportFrame.Map).distanceM
            assertTrue(d >= previous, "distance went backwards at frame $i")
            previous = d
        }
        assertEquals(p.totalM, previous, 0.5)
    }
}
