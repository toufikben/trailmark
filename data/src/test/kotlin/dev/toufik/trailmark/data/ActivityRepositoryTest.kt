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
}
