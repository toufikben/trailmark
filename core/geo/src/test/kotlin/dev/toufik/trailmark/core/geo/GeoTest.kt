package dev.toufik.trailmark.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoTest {
    @Test
    fun identicalPointsAreZero() {
        assertEquals(0.0, haversineMeters(1.0, 2.0, 1.0, 2.0), 1e-9)
    }

    @Test
    fun earthRadiusIsPinnedToOneDegreeOfLatitude() {
        // R*pi/180 = 111195.080234 m. Tolerance 1 m rejects the equatorial
        // (6378137) and polar (6356752) radii, so the constant cannot be swapped.
        assertEquals(111195.08, haversineMeters(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun bearingEastIsNinetyDegrees() {
        assertEquals(90.0, bearingDeg(36.75, 3.18, 36.75, 3.19), 0.5)
    }

    @Test
    fun aKnownAlgiersCorridorIsWithinPointThreePercent() {
        val dLon = 1000.0 / (111412.0 * 0.8013)
        val d = haversineMeters(36.7538, 3.1856, 36.7538, 3.1856 + dLon)
        assertEquals(1000.0, d, 3.0)
    }

    private data class Pt(val lat: Double, val lon: Double)

    @Test
    fun simplificationKeepsEndpointsAndDropsCollinearPoints() {
        val line = (0..10).map { Pt(36.0 + it * 0.0001, 3.0) }
        val out = douglasPeucker(line, 1.0, { it.lat }, { it.lon })
        assertEquals(2, out.size)
        assertEquals(line.first(), out.first())
        assertEquals(line.last(), out.last())
    }

    @Test
    fun simplificationKeepsSharpCorners() {
        val pts = listOf(
            Pt(36.0, 3.0),
            Pt(36.0, 3.0005),
            Pt(36.0005, 3.0005),
            Pt(36.0005, 3.001),
        )
        val out = douglasPeucker(pts, 1.0, { it.lat }, { it.lon })
        assertEquals(pts, out)
    }

    @Test
    fun simplificationDropsTinyDeviations() {
        val pts = listOf(
            Pt(36.0, 3.0),
            Pt(36.00001, 3.0005),
            Pt(36.0, 3.001),
        )
        val out = douglasPeucker(pts, 5.0, { it.lat }, { it.lon })
        assertEquals(2, out.size)
    }

    @Test
    fun simplificationHandlesShortAndEmptyInput() {
        assertEquals(emptyList<Pt>(), douglasPeucker(emptyList<Pt>(), 1.0, { it.lat }, { it.lon }))
        val one = listOf(Pt(36.0, 3.0))
        assertEquals(one, douglasPeucker(one, 1.0, { it.lat }, { it.lon }))
    }
}
