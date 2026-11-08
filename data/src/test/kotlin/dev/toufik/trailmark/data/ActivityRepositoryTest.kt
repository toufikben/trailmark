package dev.toufik.trailmark.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.UNMEASURED_ACCURACY_M
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private fun p(
    t: Long,
    lat: Double = 36.7538,
    acc: Double = 5.0,
    type: WaypointType = WaypointType.NORMAL,
    alt: Double? = 10.0,
) = StoredPoint(lat, 3.1856, t, acc, type, altitudeM = alt)

class ActivityRepositoryTest {
    private lateinit var repo: ActivityRepository

    @BeforeEach
    fun setUp() {
        repo = ActivityRepository(createDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)))
    }

    @Test
    fun pointsRoundTripInWriteOrder() {
        val id = repo.insertActivity("u1", "RUN", 1000)
        repo.appendPoints(id, listOf(p(20), p(10, type = WaypointType.IDLE)))
        assertEquals(2, repo.pointCount(id))
        val pts = repo.loadPoints(id)
        assertEquals(20, pts.first().timeNanos)
        assertEquals(WaypointType.IDLE, pts.last().type)
    }

    @Test
    fun unmeasuredAccuracyIsStoredAsNullAndReadBackAsSentinel() {
        val id = repo.insertActivity("u2", "RUN", 1)
        repo.appendPoints(id, listOf(p(5, acc = UNMEASURED_ACCURACY_M), p(6, acc = 7.5)))
        val pts = repo.loadPoints(id)
        assertTrue(pts[0].accuracyM.isInfinite())
        assertEquals(7.5, pts[1].accuracyM, 1e-9)
    }

    @Test
    fun finishWritesTotals() {
        val id = repo.insertActivity("u3", "BIKE", 1)
        repo.finishActivity(id, 123, 456.0, 7, 2, true)
        val row = repo.listActivities().single()
        assertEquals(456.0, row.distanceM, 1e-9)
        assertEquals(123, row.elapsedNanos)
        assertEquals(7, row.fixCount)
        assertEquals(2, row.rejectedCount)
        assertTrue(row.mockDetected)
    }

    @Test
    fun resumeRoundTripAndClear() {
        val id = repo.insertActivity("u4", "RUN", 1)
        repo.saveResume(id, p(9, acc = UNMEASURED_ACCURACY_M), distanceM = 222.4, elapsedNanos = 61000)
        val r = repo.loadResume()!!
        assertEquals(id, r.activityId)
        assertEquals(222.4, r.distanceM, 1e-9)
        assertEquals(61000, r.elapsedNanos)
        assertTrue(r.lastStored.accuracyM.isInfinite())
        repo.clearResume(id)
        assertNull(repo.loadResume())
    }

    @Test
    fun anOpenActivityIsMarkedAndShowsCheckpointTotals() {
        val closed = repo.insertActivity("c", "RUN", 100)
        repo.finishActivity(closed, 10, 100.0, 1, 0, false)
        val live = repo.insertActivity("l", "BIKE", 200)
        repo.saveResume(live, p(0), distanceM = 120.0, elapsedNanos = 60_000_000_000L)
        val rows = repo.listActivities()
        assertEquals(listOf(live, closed), rows.map { it.id })
        assertEquals(true, rows[0].isOpen)
        assertEquals(120.0, rows[0].distanceM, 1e-9)
        assertEquals(60_000_000_000L, rows[0].elapsedNanos)
        assertFalse(rows[1].isOpen)
    }

    @Test
    fun flagsRoundTripWithDefaults() {
        assertFalse(repo.flag(ONBOARDED_FLAG))
        assertTrue(repo.flag("missing", default = true))
        repo.setFlag(ONBOARDED_FLAG, true)
        assertTrue(repo.flag(ONBOARDED_FLAG))
    }

    @Test
    fun mediaRoundTripsInRouteOrder() {
        val id = repo.insertActivity("m1", "HIKE", 1000)
        val photoId = repo.insertMedia(
            activityId = id,
            kind = "PHOTO",
            filePath = "/data/media/p1.jpg",
            lat = 36.75,
            lon = 3.18,
            bearingDeg = 90.0,
            distanceM = 1200.0,
            elapsedNanos = 600_000_000_000L,
            createdWallMillis = 2000,
            durationMs = null,
            caption = "viewpoint",
        )
        repo.insertMedia(id, "VIDEO", "/data/media/v1.mp4", null, null, null, 300.0, 120_000_000_000L, 1500, 8000, null)
        val rows = repo.listMedia(id)
        assertEquals(2, rows.size)
        assertEquals("VIDEO", rows[0].kind)
        assertEquals("PHOTO", rows[1].kind)
        assertEquals(photoId, rows[1].id)
        assertEquals(1200.0, rows[1].distanceM, 1e-9)
        assertEquals("viewpoint", rows[1].caption)
        assertNull(rows[0].lat)
        assertEquals(8000L, rows[0].durationMs)
    }

    @Test
    fun mediaCaptionIsEditableAndClearable() {
        val id = repo.insertActivity("m4", "RUN", 3)
        val mediaId = repo.insertMedia(id, "PHOTO", "/p2.jpg", null, null, null, 0.0, 0L, 1, null, null)
        repo.setMediaCaption(mediaId, "sunrise at the pass")
        assertEquals("sunrise at the pass", repo.listMedia(id).single().caption)
        repo.setMediaCaption(mediaId, null)
        assertNull(repo.listMedia(id).single().caption)
    }

    @Test
    fun mediaIsScopedToItsActivityAndDeletable() {
        val a = repo.insertActivity("m2", "RUN", 1)
        val b = repo.insertActivity("m3", "RUN", 2)
        val mediaId = repo.insertMedia(a, "PHOTO", "/p.jpg", null, null, null, 0.0, 0L, 1, null, null)
        repo.insertMedia(b, "PHOTO", "/q.jpg", null, null, null, 0.0, 0L, 1, null, null)
        assertEquals(1, repo.listMedia(a).size)
        repo.deleteMedia(mediaId)
        assertTrue(repo.listMedia(a).isEmpty())
        assertEquals(1, repo.listMedia(b).size)
    }

    @Test
    fun pointsAfterReturnsOnlyNewerIdsInOrder() {
        val id = repo.insertActivity("u5", "RUN", 1000)
        val other = repo.insertActivity("u6", "RUN", 1001)
        repo.appendPoints(id, listOf(p(20), p(10), p(30)))
        repo.appendPoints(other, listOf(p(99)))
        val all = repo.pointsAfter(id, 0L)
        assertEquals(listOf(20L, 10L, 30L), all.map { it.second.timeNanos })
        val cursor = all[1].first
        val newer = repo.pointsAfter(id, cursor)
        assertEquals(listOf(30L), newer.map { it.second.timeNanos })
        assertTrue(newer.all { it.first > cursor })
        assertTrue(repo.pointsAfter(id, all.last().first).isEmpty())
    }

    @Test
    fun segmentsAndEffortsRoundTrip() {
        val activity = repo.insertActivity("s1", "RUN", 1000)
        repo.finishActivity(activity, 10, 5000.0, 1, 0, false)
        val seg = repo.insertSegment("Hill", 36.0, 3.0, 36.01, 3.01)
        assertFalse(repo.effortExists(seg, activity))
        assertNull(repo.bestEffortNanos(seg))
        val emptyStat = repo.segmentStats().single()
        assertEquals(0L, emptyStat.attempts)
        assertNull(emptyStat.bestNanos)
        repo.insertEffort(seg, activity, 300_000_000_000L)
        assertTrue(repo.effortExists(seg, activity))
        assertEquals(300_000_000_000L, repo.bestEffortNanos(seg))
        val stat = repo.segmentStats().single()
        assertEquals("Hill", stat.name)
        assertEquals(1L, stat.attempts)
        assertEquals(300_000_000_000L, stat.bestNanos)
        val row = repo.listSegments().single()
        assertEquals("Hill", row.name)
        assertEquals(36.01, row.endLat, 1e-9)
        val efforts = repo.effortsForActivity(activity)
        assertEquals(1, efforts.size)
        assertEquals("Hill", efforts.single().segmentName)
        assertEquals(300_000_000_000L, efforts.single().elapsedNanos)
        assertTrue(repo.effortsForActivity(activity + 5).isEmpty())
    }

    @Test
    fun weatherSnapshotRoundTrips() {
        val id = repo.insertActivity("w1", "RUN", 1)
        assertNull(repo.listActivities().single().weatherTempC)
        repo.setWeather(id, tempC = 21.5, windKmh = 14.0, windDeg = 270, code = 3)
        val row = repo.activityById(id)!!
        assertEquals(21.5, row.weatherTempC ?: -1.0, 1e-9)
        assertEquals(14.0, row.weatherWindKmh ?: -1.0, 1e-9)
        assertEquals(270L, row.weatherWindDeg)
        assertEquals(3L, row.weatherCode)
    }

    @Test
    fun deleteActivityRemovesEverythingItOwns() {
        val keep = repo.insertActivity("keep", "RUN", 1000)
        repo.appendPoints(keep, listOf(p(10), p(20)))
        val gone = repo.insertActivity("gone", "RUN", 2000)
        repo.appendPoints(gone, listOf(p(10), p(20), p(30)))
        repo.insertMedia(gone, "PHOTO", "/m/a.jpg", 36.0, 3.0, null, 10.0, 5, 1, null, null)
        repo.insertMedia(gone, "VIDEO", "/m/b.mp4", 36.0, 3.0, null, 20.0, 9, 2, 4000, null)
        val seg = repo.insertSegment("hill", 36.0, 3.0, 36.1, 3.1)
        repo.insertEffort(seg, gone, 1_000)
        repo.saveResume(gone, p(30), 12.0, 30)

        val files = repo.deleteActivity(gone)

        assertEquals(setOf("/m/a.jpg", "/m/b.mp4"), files.toSet())
        assertEquals(listOf(keep), repo.listActivities().map { it.id })
        assertEquals(0, repo.pointCount(gone))
        assertTrue(repo.listMedia(gone).isEmpty())
        assertTrue(repo.effortsForActivity(gone).isEmpty())
        assertNull(repo.loadResume())
        assertEquals(2, repo.pointCount(keep))
    }

    @Test
    fun segmentBoardRanksEffortsFastestFirstAndDeletes() {
        val a1 = repo.insertActivity("a1", "RUN", 1000)
        val a2 = repo.insertActivity("a2", "RUN", 2000)
        val a3 = repo.insertActivity("a3", "RUN", 3000)
        val seg = repo.insertSegment("climb", 36.0, 3.0, 36.1, 3.1)
        repo.insertEffort(seg, a1, 400)
        repo.insertEffort(seg, a2, 300)
        repo.insertEffort(seg, a3, 500)

        val board = repo.effortsForSegment(seg)
        assertEquals(listOf(a2, a1, a3), board.map { it.activityId })
        assertEquals(2000L, board.first().startWallMillis)

        repo.deleteSegment(seg)
        assertTrue(repo.listSegments().isEmpty())
        assertTrue(repo.effortsForSegment(seg).isEmpty())
    }
}
