package dev.toufik.trailmark.core.geo

import kotlin.math.PI
import kotlin.math.cos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoProjectionTest {
    @Test
    fun preservesAspectForASymmetricRoute() {
        val lat = 36.7538
        val c = cos(lat * PI / 180)
        val dLon = 0.002
        val lats = listOf(lat - dLon * c / 2, lat + dLon * c / 2)
        val lons = listOf(3.1856 - dLon / 2, 3.1856 + dLon / 2)
        val p = GeoProjection.of(lats, lons, 300.0, 300.0)!!
        val dx = kotlin.math.abs(p.x(lons[1]) - p.x(lons[0]))
        val dy = kotlin.math.abs(p.y(lats[0]) - p.y(lats[1]))
        assertEquals(dx, dy, 0.001)
    }

    @Test
    fun aZeroExtentRouteReportsNoScale() {
        val p = GeoProjection.of(listOf(1.0, 1.0), listOf(2.0, 2.0), 300.0, 300.0)!!
        assertEquals(0.0, p.metresPerPixel, 1e-9)
    }

    @Test
    fun aRealRouteHasAPositiveScale() {
        val p = GeoProjection.of(listOf(36.7538, 36.7558), listOf(3.1856, 3.1876), 300.0, 300.0)!!
        assertTrue(p.metresPerPixel > 0)
    }

    @Test
    fun emptyInputIsNull() {
        assertNull(GeoProjection.of(emptyList(), emptyList(), 300.0, 300.0))
    }
}
