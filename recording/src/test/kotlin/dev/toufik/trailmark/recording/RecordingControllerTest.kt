package dev.toufik.trailmark.recording

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.ActivityRepository
import dev.toufik.trailmark.data.createDatabase
import dev.toufik.trailmark.engine.FixSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class ListSource(private val list: List<RawFix>) : FixSource {
    override fun fixes(): Flow<RawFix> = flow { list.forEach { emit(it) } }
}

private class GatedSource(private val gates: List<CompletableDeferred<Unit>>) : FixSource {
    override fun fixes(): Flow<RawFix> = flow {
        walkRange(0).forEach { emit(it) }
        gates[0].await()
        walkRange(3).forEach { emit(it) }
        gates[1].await()
        walkRange(6).forEach { emit(it) }
    }
}

private fun walkFixes(n: Int): List<RawFix> = (0 until n).map { i ->
    RawFix(36.7538 + i * (10.0 / 111111.0), 3.1856, i * 10_000_000_000L, 3.0, speedMps = 1.0)
}

private fun walkRange(start: Int): List<RawFix> = (start until start + 3).map { i ->
    RawFix(36.7538 + i * (10.0 / 111111.0), 3.1856, i * 10_000_000_000L, 3.0, speedMps = 1.0)
}

private fun repo(): ActivityRepository =
    ActivityRepository(createDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)))

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingControllerTest {
    @Test
    fun recordsEveryStoredPointAndFinalizesOnStop() = runTest {
        val r = repo()
        val c = RecordingController(r, ListSource(walkFixes(5)), this, nowNanos = { 0L }, tickEnabled = false)
        c.start()
        advanceUntilIdle()

        assertEquals(Phase.RECORDING, c.state.value.phase)
        assertTrue(c.state.value.distanceM > 0, "distance accumulated")
        assertTrue(c.state.value.fixCount > 0)

        c.stop()
        val rows = r.listActivities()
        assertEquals(1, rows.size)
        assertTrue(rows[0].distanceM > 0, "finishActivity wrote the totals")
        assertNull(r.loadResume(), "a clean stop clears the checkpoint")
        assertEquals(rows[0].id, c.state.value.lastActivityId)
        assertEquals(Phase.STOPPED, c.state.value.phase)
    }

    @Test
    fun distanceCarriesFromTheCheckpointOnResume() = runTest {
        val r = repo()
        val id = r.insertActivity("prior", "RUN", 1L)
        r.saveResume(
            activityId = id,
            lastStored = StoredPoint(36.7538, 3.1856, 0L, 5.0, WaypointType.NORMAL),
            distanceM = 100.0,
            elapsedNanos = 50_000_000_000L,
        )
        val c = RecordingController(r, ListSource(walkFixes(4)), this, nowNanos = { 0L }, tickEnabled = false)
        c.start()
        advanceUntilIdle()

        assertTrue(c.state.value.distanceM > 100.0, "distance continues from the checkpoint")
        c.stop()
        val rows = r.listActivities()
        assertEquals(1, rows.size, "the resume reuses the same activity row")
        assertEquals(id, rows[0].id)
        assertTrue(rows[0].elapsedNanos > 50_000_000_000L, "persisted elapsed accumulates")
    }

    @Test
    fun aBadSignalKeepsSearchingAndRecordsNothing() = runTest {
        val r = repo()
        val bad = (0 until 5).map {
            RawFix(36.7538 + it * (10.0 / 111111.0), 3.1856, it * 10_000_000_000L, 90.0, speedMps = 1.0)
        }
        val c = RecordingController(r, ListSource(bad), this, nowNanos = { 0L }, tickEnabled = false)
        c.start()
        advanceUntilIdle()

        assertEquals(Phase.STARTING, c.state.value.phase)
        assertEquals(dev.toufik.trailmark.engine.GpsQuality.SEARCHING, c.state.value.quality)
        c.stop()
        assertTrue(r.listActivities().isEmpty(), "no row for a session O�7 never opened")
    }

    @Test
    fun pauseFreezesTimeAndDistanceAndResumeContinues() = runTest {
        val r = repo()
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        val c = RecordingController(
            r, GatedSource(listOf(gate1, gate2)), this, nowNanos = { 0L }, tickEnabled = false,
        )
        c.start()
        advanceUntilIdle()

        assertEquals(Phase.RECORDING, c.state.value.phase)
        val d0 = c.state.value.distanceM
        val e0 = c.state.value.elapsedNanos
        assertTrue(d0 > 0.0, "first segment recorded")

        c.pause()
        assertEquals(Phase.PAUSED, c.state.value.phase)

        gate1.complete(Unit)
        advanceUntilIdle()
        assertEquals(d0, c.state.value.distanceM, "distance frozen while paused")
        assertEquals(e0, c.state.value.elapsedNanos, "time frozen while paused")

        c.resume()
        assertEquals(Phase.RECORDING, c.state.value.phase)

        gate2.complete(Unit)
        advanceUntilIdle()
        assertTrue(c.state.value.distanceM > d0, "distance continues after resume")
        assertTrue(c.state.value.elapsedNanos > e0, "time continues after resume")
        assertTrue(c.state.value.elapsedNanos < 60_000_000_000L, "the paused gap is not counted")

        c.stop()
        val rows = r.listActivities()
        assertEquals(1, rows.size)
        assertTrue(rows[0].distanceM > d0, "stored totals include both segments")
        assertTrue(rows[0].elapsedNanos < 60_000_000_000L, "paused gap excluded from stored total")
    }
}
}
