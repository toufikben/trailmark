package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalRecordsTest {

    /** Eastward points on the equator; 0.001 deg of lon is ~111.19 m. */
    private fun route(stepSeconds: List<Double>): List<StoredPoint> {
        var nanos = 0L
        return stepSeconds.mapIndexed { i, sec ->
            val p = StoredPoint(
                lat = 0.0,
                lon = i * 0.001,
                timeNanos = nanos,
                accuracyM = 5.0,
                type = WaypointType.NORMAL,
            )
            nanos += (sec * 1_000_000_000L).toLong()
            p
        }
    }

    @Test
    fun fastestKilometreTakesTheFastestNineSteps() {
        val even = route(List(20) { 10.0 })
        assertEquals(90.0, fastestSplitSeconds(even, 1000.0)!!, 1e-6)

        val fastTail = route(List(10) { 10.0 } + List(10) { 5.0 })
        assertEquals(45.0, fastestSplitSeconds(fastTail, 1000.0)!!, 1e-6)
    }

    @Test
    fun shortRouteHasNoSplit() {
        val short = route(List(5) { 10.0 })
        assertNull(fastestSplitSeconds(short, 1000.0))
        assertNull(fastestSplitSeconds(emptyList(), 1000.0))
        assertNull(fastestSplitSeconds(listOf(short.first()), 1000.0))
    }

    @Test
    fun rejectedGapCountsAsDistanceLikeAnalysisDoes() {
        var nanos = 0L
        val points = ArrayList<StoredPoint>()
        for (i in 0..20) {
            val type =
                if (i in 5..13) WaypointType.REJECTED else WaypointType.NORMAL
            points += StoredPoint(0.0, i * 0.001, nanos, 5.0, type)
            nanos += 10_000_000_000L
        }
        // Rejected points are dropped, so the 4 -> 14 jump is one 1111 m step
        // spanning 100 s: exactly how analyze() counts distance across a gap.
        assertEquals(100.0, fastestSplitSeconds(points, 1000.0)!!, 1e-6)
    }

    @Test
    fun aPauseGapIsNotTravelledDistanceForARecord() {
        // Pause, drive 5 km, resume. The two legs are separate; the drive used
        // to count as distance and hand out a record nobody ran.
        var nanos = 0L
        val points = ArrayList<StoredPoint>()
        for (i in 0..10) {
            points += StoredPoint(0.0, i * 0.001, nanos, 5.0, WaypointType.NORMAL)
            nanos += 10_000_000_000L
        }
        // 5 km east, then a RESUME point, then one more short leg.
        points += StoredPoint(0.0, 0.045, nanos, 5.0, WaypointType.RESUME)
        nanos += 10_000_000_000L
        points += StoredPoint(0.0, 0.046, nanos, 5.0, WaypointType.NORMAL)

        // The first leg is only ~1.1 km, so a 5 km record cannot exist at all.
        assertNull(fastestSplitSeconds(points, 5000.0))
    }

    @Test
    fun personalRecordsPicksBestAcrossRoutes() {
        val slow = route(List(60) { 10.0 })
        val fast = route(List(60) { 8.0 })
        val tiny = route(List(3) { 4.0 })
        val records = personalRecords(listOf(slow, fast, tiny))
        assertEquals(72.0, records.best1kSec!!, 1e-6)
        assertTrue(records.best5kSec != null)
        assertNull(records.best10kSec)
        assertTrue(!records.isEmpty)
    }
}
