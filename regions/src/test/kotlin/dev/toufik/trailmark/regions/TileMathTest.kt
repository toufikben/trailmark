package dev.toufik.trailmark.regions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileMathTest {

    @Test
    fun bboxAroundIsSymmetricAtTheEquator() {
        val b = TileMath.bboxAround(0.0, 0.0, 10.0)
        assertEquals(-b[0], b[2], 1e-9)
        assertEquals(-b[1], b[3], 1e-9)
        assertEquals(2 * 10.0 / TileMath.KM_PER_DEGREE, b[3] - b[1], 1e-6)
    }

    @Test
    fun bboxAroundWidensLongitudeTowardsThePoles() {
        val equator = TileMath.bboxAround(0.0, 0.0, 10.0)
        val sixty = TileMath.bboxAround(60.0, 0.0, 10.0)
        val wEquator = equator[2] - equator[0]
        val wSixty = sixty[2] - sixty[0]
        assertTrue(wSixty > wEquator * 1.9, "60N width $wSixty vs equator $wEquator")
    }
    @Test
    fun tileCountGrowsWithRadiusAndZoom() {
        val small = TileMath.bboxAround(36.7538, 3.0588, 5.0)
        val big = TileMath.bboxAround(36.7538, 3.0588, 10.0)
        val n5 = TileMath.tileCount(small[0], small[1], small[2], small[3], 11, 15)
        val n10 = TileMath.tileCount(big[0], big[1], big[2], big[3], 11, 15)
        assertTrue(n5 in 60..400, "5km area tiles: $n5")
        assertTrue(n10 > n5 * 3, "10km should be about 4x 5km: $n10 vs $n5")
        val n5low = TileMath.tileCount(small[0], small[1], small[2], small[3], 11, 13)
        assertTrue(n5low < n5, "lower ceiling should cost less: $n5low vs $n5")
    }

    @Test
    fun tileCoordinatesClampAtTheEdges() {
        assertEquals(0L, TileMath.tileX(-180.0, 4))
        assertEquals(15L, TileMath.tileX(180.0, 4))
        assertEquals(0L, TileMath.tileY(85.0, 4))
        assertEquals(15L, TileMath.tileY(-85.0, 4))
    }
}
