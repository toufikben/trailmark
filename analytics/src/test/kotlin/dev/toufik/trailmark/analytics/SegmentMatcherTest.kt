package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentMatcherTest {

    private val segment = Segment(
        id = 1,
        name = "Hill",
        startLat = 36.0,
        startLon = 3.0,
        endLat = 36.001,
        endLon = 3.001,
    )

    private fun pt(
        lat: Double,
        lon: Double,
        nanos: Long,
        type: WaypointType = WaypointType.NORMAL,
    ) = StoredPoint(lat, lon, nanos, 5.0, type)

    @Test
    fun fullPassageReturnsElapsedTime() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.0005, 3.0005, 5_000_000_000L),
            pt(36.001, 3.001, 12_000_000_000L),
        )
        assertEquals(12_000_000_000L, matchEffort(segment, track))
    }

    @Test
    fun neverNearStartReturnsNull() {
        val track = listOf(
            pt(36.05, 3.05, 0),
            pt(36.06, 3.06, 5_000_000_000L),
        )
        assertNull(matchEffort(segment, track))
    }

    @Test
    fun startWithoutEndReturnsNull() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.0002, 3.0002, 3_000_000_000L),
        )
        assertNull(matchEffort(segment, track))
    }

    @Test
    fun rejectedPointsDoNotCountAsEntry() {
        val track = listOf(
            pt(36.0, 3.0, 0, WaypointType.REJECTED),
            pt(36.0005, 3.0005, 5_000_000_000L),
            pt(36.001, 3.001, 10_000_000_000L),
        )
        assertNull(matchEffort(segment, track))
    }

    @Test
    fun firstPassageWinsOverFasterSecondRun() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.001, 3.001, 10_000_000_000L),
            pt(36.0, 3.0, 20_000_000_000L),
            pt(36.001, 3.001, 25_000_000_000L),
        )
        assertEquals(10_000_000_000L, matchEffort(segment, track))
    }

    @Test
    fun gapBetweenPassesBreaksEntry() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.005, 3.005, 4_000_000_000L),
            pt(36.001, 3.001, 9_000_000_000L),
        )
        assertEquals(9_000_000_000L, matchEffort(segment, track))
    }
}
