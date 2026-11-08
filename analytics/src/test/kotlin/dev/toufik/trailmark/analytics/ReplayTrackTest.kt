package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun rp(t: Long, lat: Double) =
    StoredPoint(lat, 3.1856, t, 5.0, WaypointType.NORMAL)

class ReplayTrackTest {
    @Test
    fun samplesMidSegmentByTime() {
        val track = ReplayTrack(listOf(rp(0L, 36.0), rp(10_000_000_000L, 36.1)))
        val s = track.sampleAt(5_000_000_000L)!!
        assertEquals(36.05, s.lat, 1e-9)
        assertEquals(5_000_000_000L, s.nanos)
        assertTrue(s.distanceM > 0.0 && s.distanceM < track.totalDistanceM)
    }

    @Test
    fun clampsAndReportsEnds() {
        val track = ReplayTrack(listOf(rp(0L, 36.0), rp(10_000_000_000L, 36.1)))
        assertEquals(36.0, track.sampleAt(-5L)!!.lat, 1e-9)
        val end = track.sampleAt(999_000_000_000L)!!
        assertEquals(36.1, end.lat, 1e-9)
        assertEquals(track.totalNanos, end.nanos)
        assertEquals(track.totalDistanceM, end.distanceM, 1e-6)
        assertEquals(10_000_000_000L, track.totalNanos)
    }

    @Test
    fun skipsRejectedAndSurvivesClockJumps() {
        val rejected = StoredPoint(35.0, 3.0, 2_000_000_000L, 5.0, WaypointType.REJECTED)
        val track = ReplayTrack(
            listOf(rp(0L, 36.0), rejected, rp(5_000_000_000L, 36.1), rp(3_000_000_000L, 36.2)),
        )
        assertEquals(3, track.size)
        // Last point's clock jumped backwards: time stays at the previous mark.
        assertEquals(5_000_000_000L, track.totalNanos)
        val s = track.sampleAt(track.totalNanos)!!
        assertTrue(s.lat >= 36.1, "the rejected spike must never be sampled")
    }

    @Test
    fun pinsMediaByPlaceNotMovingTime() {
        val min = 60_000_000_000L
        // 10 minutes moving, a 50-minute stop, then 10 more minutes moving.
        val track = ReplayTrack(
            listOf(
                rp(0L, 36.00),
                rp(10 * min, 36.01),
                rp(60 * min, 36.01),
                rp(65 * min, 36.015),
                rp(70 * min, 36.02),
            ),
        )
        val photo = track.sampleAt(65 * min)!!
        // Moving time says 15 min, but the place is reached 65 min into the replay.
        assertEquals(65 * min, track.nanosNear(photo.lat, photo.lon, photo.distanceM))
    }

    @Test
    fun picksThePassMatchingTheDistanceOnARepeatedSpot() {
        val s = 1_000_000_000L
        // Out to the north and back: the start latitude is passed twice.
        val track = ReplayTrack(listOf(rp(0L, 36.00), rp(100 * s, 36.01), rp(200 * s, 36.00)))
        val outbound = track.nanosNear(36.00, 3.1856, 0.0)
        val back = track.nanosNear(36.00, 3.1856, track.totalDistanceM)
        assertEquals(0L, outbound)
        assertEquals(200 * s, back)
    }

    @Test
    fun fallsBackToDistanceWithoutAPosition() {
        val track = ReplayTrack(listOf(rp(0L, 36.0), rp(10_000_000_000L, 36.1)))
        val mid = track.nanosNear(null, null, track.totalDistanceM / 2)
        assertEquals(5_000_000_000.0, mid.toDouble(), 1e3)
        assertEquals(0L, track.nanosNear(null, null, -3.0))
        assertEquals(track.totalNanos, track.nanosNear(null, null, 1e9))
        assertEquals(0L, ReplayTrack(emptyList()).nanosNear(36.0, 3.0, 10.0))
    }

    @Test
    fun emptyAndSinglePoint() {
        assertNull(ReplayTrack(emptyList()).sampleAt(0L))
        val one = ReplayTrack(listOf(rp(0L, 36.0)))
        assertEquals(36.0, one.sampleAt(1L)!!.lat, 1e-9)
        assertEquals(0L, one.totalNanos)
        assertEquals(0.0, one.totalDistanceM, 1e-9)
    }
}
