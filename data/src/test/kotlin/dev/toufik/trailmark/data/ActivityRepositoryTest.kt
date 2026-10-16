package dev.toufik.trailmark.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.UNMEASURED_ACCURACY_M
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
}
