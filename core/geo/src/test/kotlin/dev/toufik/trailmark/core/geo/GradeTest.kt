package dev.toufik.trailmark.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GradeTest {

    private fun pt(ele: Double, i: Int = 0): ElevPoint =
        ElevPoint(lat = 36.25, lon = 2.23 + i * 0.0, eleM = ele)

    @Test
    fun pureClimbCountsFullAscentNoDescent() {
        val pts = listOf(pt(0.0), pt(10.0), pt(20.0))
        val (up, down) = ascentDescent(pts)
        assertEquals(20.0, up, 1e-9)
        assertEquals(0.0, down, 1e-9)
    }

    @Test
    fun subThresholdJitterBillsNothing() {
        val pts = listOf(pt(100.0), pt(100.4), pt(99.8), pt(100.2), pt(99.9))
        val (up, down) = ascentDescent(pts)
        assertEquals(0.0, up, 1e-9)
        assertEquals(0.0, down, 1e-9)
    }

    @Test
    fun upThenDownSplitsBothWays() {
        val pts = listOf(pt(0.0), pt(30.0), pt(10.0))
        val (up, down) = ascentDescent(pts)
        assertEquals(30.0, up, 1e-9)
        assertEquals(20.0, down, 1e-9)
    }

    @Test
    fun chunksAreContiguousAndCarryGrade() {
        // 11 points, 50 m apart eastward, +10 m per step -> a steady 20% climb.
        val dLon = 50.0 / (111195.08 * 0.8063)
        val pts = (0..10).map { i ->
            ElevPoint(lat = 36.25, lon = 2.23 + i * dLon, eleM = i * 10.0)
        }
        val chunks = gradeChunks(pts, maxChunkM = 120.0)
        assertTrue(chunks.size >= 4)
        for (c in chunks) assertEquals(20.0, c.gradePercent, 0.1)
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].points.last(), chunks[i].points.first())
        }
        val total = chunks.sumOf { it.lengthM }
        assertEquals(500.0, total, 5.0)
    }

    @Test
    fun flatRouteIsZeroGrade() {
        val dLon = 100.0 / (111195.08 * 0.8063)
        val pts = (0..4).map { i -> ElevPoint(36.25, 2.23 + i * dLon, 300.0) }
        val chunks = gradeChunks(pts, maxChunkM = 120.0)
        for (c in chunks) assertEquals(0.0, c.gradePercent, 1e-6)
    }

    @Test
    fun durationIsFlatSpeedPlusClimbPenalty() {
        // 10 km at 10 km/h flat = 3600 s; +100 m climb at 5 s/m = +500 s.
        val s = estimateDurationSeconds(10_000.0, 100.0, 10.0, 5.0)
        assertEquals(4100.0, s, 1e-6)
    }

    @Test
    fun durationIgnoresNegativeAscent() {
        assertEquals(3600.0, estimateDurationSeconds(10_000.0, -50.0, 10.0, 5.0), 1e-6)
    }

    @Test
    fun elevationRangeAddsMargin() {
        val pts = listOf(pt(120.0), pt(100.0), pt(150.0))
        val (lo, hi) = elevationRangeM(pts, marginM = 5.0)
        assertEquals(95.0, lo, 1e-9)
        assertEquals(155.0, hi, 1e-9)
    }

    @Test
    fun maxAbsGradeTakesSteepestDirection() {
        val a = RouteChunk(listOf(pt(0.0), pt(10.0)), gradePercent = 8.0, lengthM = 100.0)
        val b = RouteChunk(listOf(pt(10.0), pt(0.0)), gradePercent = -12.5, lengthM = 100.0)
        assertEquals(12.5, maxAbsGradePercent(listOf(a, b)), 1e-9)
    }

    @Test
    fun polylineLengthMatchesSingleHop() {
        val dLon = 1000.0 / (111195.08 * 0.8063)
        val pts = listOf(ElevPoint(36.25, 2.23, 0.0), ElevPoint(36.25, 2.23 + dLon, 0.0))
        assertEquals(1000.0, polylineLengthMeters(pts), 3.0)
    }
}
