package dev.toufik.trailmark.app.export

import dev.toufik.trailmark.app.ui.FLYOVER_FPS
import dev.toufik.trailmark.app.ui.FLYOVER_INTRO_FRAMES
import dev.toufik.trailmark.app.ui.FLYOVER_OUTRO_FRAMES
import dev.toufik.trailmark.app.ui.FlyoverPath
import dev.toufik.trailmark.app.ui.FlyoverPhase
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.MediaRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A point [m] metres north of the start at [sec] seconds. */
private fun at(m: Double, sec: Long, type: WaypointType = WaypointType.NORMAL) =
    StoredPoint(36.0 + m / 111_195.0, 3.0, sec * 1_000_000_000L, 5.0, type)

private var nextMediaId = 1L

/** A photo [m] metres into the route, as the recorder would store it. */
private fun photo(m: Double) = MediaRow(
    id = nextMediaId++,
    activityId = 1L,
    kind = "PHOTO",
    filePath = "/tmp/photo.jpg",
    lat = null,
    lon = null,
    bearingDeg = null,
    distanceM = m,
    elapsedNanos = m.toLong() * 1_000_000L,
    createdWallMillis = 0L,
    durationMs = null,
    caption = null,
)

/** A clip that is normally built by [ExportTimeline.clipsFor]. */
private fun clip(m: Double, cinematic: Boolean = true) = MediaClip(
    media = photo(m),
    atDistanceM = m,
    holdFrames = PHOTO_SECONDS * FLYOVER_FPS,
    leadFrames = if (cinematic) (MEDIA_LEAD_SECONDS * FLYOVER_FPS).toInt() else 0,
    tailFrames = if (cinematic) (MEDIA_TAIL_SECONDS * FLYOVER_FPS).toInt() else 0,
)

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

    @Test
    fun aCinematicClipDescendsHoldsAndClimbsOut() {
        val c = clip(1_000.0)
        assertEquals(
            PHOTO_SECONDS * FLYOVER_FPS + c.leadFrames + c.tailFrames,
            c.frames,
            "a clip is its hold plus the descent and the climb-out",
        )
        val timeline = ExportTimeline(path(), routeSeconds = 15, clips = listOf(c))
        val first = (0 until timeline.frames)
            .map { timeline.frame(it) }
            .filterIsInstance<ExportFrame.Media>()
            .first()
        assertEquals(MediaStage.LEAD, first.stage)
        assertEquals(0.0, first.stageT, 0.01)

        val media = (0 until timeline.frames)
            .map { timeline.frame(it) }
            .filterIsInstance<ExportFrame.Media>()
        assertEquals(c.frames, media.size)
        val hold = media.first { it.stage == MediaStage.HOLD }
        assertEquals(0.0, hold.stageT, 0.01)
        val last = media.last()
        assertEquals(MediaStage.TAIL, last.stage)
        assertEquals(1.0, last.stageT, 0.01)
    }

    @Test
    fun withoutCinematicTheCardShowsAtOnce() {
        val c = clip(1_000.0, cinematic = false)
        assertEquals(PHOTO_SECONDS * FLYOVER_FPS, c.frames)
        val timeline = ExportTimeline(path(), routeSeconds = 15, clips = listOf(c))
        val media = (0 until timeline.frames)
            .map { timeline.frame(it) }
            .filterIsInstance<ExportFrame.Media>()
        assertEquals(c.frames, media.size)
        assertTrue(media.all { it.stage == MediaStage.HOLD })
    }

    @Test
    fun twoShotsOfTheSameSpotAreShownOnce() {
        val timeline = ExportTimeline(
            path(),
            routeSeconds = 15,
            clips = listOf(clip(500.0), clip(520.0), clip(1_500.0)),
        )
        val shown = (0 until timeline.frames)
            .map { timeline.frame(it) }
            .filterIsInstance<ExportFrame.Media>()
            .map { it.clip }
            .distinct()
        assertEquals(2, shown.size, "the shot 20 m after the first is the same place")
    }

    @Test
    fun mediaNeverSwallowsTheRoute() {
        val clips = (1..8).map { clip(it * 300.0) }
        val routeSeconds = 15
        val timeline = ExportTimeline(path(), routeSeconds = routeSeconds, clips = clips)
        val mediaFrames = (0 until timeline.frames)
            .count { timeline.frame(it) is ExportFrame.Media }
        val routeFrames = routeSeconds * FLYOVER_FPS
        assertTrue(
            mediaFrames <= (routeFrames * MEDIA_MAX_SHARE).toInt(),
            "$mediaFrames media frames out of $routeFrames route frames",
        )
        assertTrue(mediaFrames > 0, "at least one shot survives the budget")
    }
}
