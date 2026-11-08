package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val S = 1_000_000_000L

/** A point [metres] north of the start, at [sec] seconds. */
private fun at(metres: Double, sec: Long, type: WaypointType = WaypointType.NORMAL) =
    StoredPoint(36.0 + metres / 111_195.0, 3.0, sec * S, 5.0, type)

class ZonesTest {
    @Test
    fun runPaceZonesSplitTimeBySpeed() {
        // 10 s at 2 m/s (8:20 /km, zone 0), then 10 s at 4 m/s (4:10 /km, zone 4).
        val pts = listOf(at(0.0, 0), at(20.0, 10), at(60.0, 20))
        val z = zoneTimes(pts, ZoneScale.RUN)
        assertArrayEquals(longArrayOf(10 * S, 0, 0, 0, 10 * S), z)
    }

    @Test
    fun idleRejectedAndLongGapsAreNotCounted() {
        val pts = listOf(
            at(0.0, 0),
            at(30.0, 10),
            at(30.0, 40, WaypointType.IDLE),
            at(9_000.0, 41, WaypointType.REJECTED),
            at(60.0, 50),
            at(90.0, 200),
        )
        val z = zoneTimes(pts, ZoneScale.RUN)
        // Only the first 10 s step counts: the IDLE point ends it, the step out of
        // IDLE is excluded, the spike is skipped and the 150 s gap is a pause.
        assertEquals(10 * S, z.sum())
    }

    @Test
    fun bikeZonesUseKilometresPerHour() {
        assertEquals(0, ZoneScale.BIKE.zoneOf(10 / 3.6))
        assertEquals(2, ZoneScale.BIKE.zoneOf(22 / 3.6))
        assertEquals(4, ZoneScale.BIKE.zoneOf(40 / 3.6))
        assertEquals(5, ZoneScale.forSport("BIKE").count)
        assertEquals(ZoneScale.RUN, ZoneScale.forSport("unknown"))
    }

    @Test
    fun percentChangeHandlesEmptyPreviousPeriod() {
        assertEquals(50, percentChange(15.0, 10.0))
        assertEquals(-25, percentChange(7.5, 10.0))
        assertNull(percentChange(5.0, 0.0))
    }
}
