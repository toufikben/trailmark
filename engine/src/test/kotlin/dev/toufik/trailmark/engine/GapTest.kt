package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GapTest {
    @Test
    fun flatGradeHasUnitFactor() {
        val f = Gap.costFactor(0.0)
        assertTrue(Math.abs(f - 1.0) < 1e-9, "flat factor was $f")
    }

    @Test
    fun uphillSlowsAndDownhillSpeedsUp() {
        assertTrue(Gap.costFactor(0.05) < 1.0)
        assertTrue(Gap.costFactor(-0.05) > 1.0)
    }

    @Test
    fun factorStaysSaneAtExtremes() {
        val steepUp = Gap.costFactor(5.0)
        val steepDown = Gap.costFactor(-5.0)
        assertTrue(steepUp in 0.2..2.0)
        assertTrue(steepDown in 0.2..2.0)
    }

    private fun point(
        lat: Double,
        lon: Double,
        t: Long,
        alt: Double?,
    ) = StoredPoint(
        lat = lat,
        lon = lon,
        timeNanos = t,
        accuracyM = 5.0,
        type = WaypointType.NORMAL,
        altitudeM = alt,
    )

    @Test
    fun nullWhenTrackTooShort() {
        val pts = listOf(
            point(36.0, 3.0, 0L, 100.0),
            point(36.00001, 3.0, 1_000_000_000L, 100.0),
        )
        assertNull(Gap.gapPaceSecPerKm(pts))
    }

    @Test
    fun nullWhenAltitudeMissing() {
        val pts = (0..9).map { i ->
            point(36.0 + i * 0.0002, 3.0, i * 1_000_000_000L, null)
        }
        assertNull(Gap.gapPaceSecPerKm(pts))
    }

    @Test
    fun uphillTrackHasSlowerGapThanFlat() {
        val flat = (0..9).map { i ->
            point(36.0 + i * 0.0002, 3.0, i * 1_000_000_000L, 100.0)
        }
        val uphill = (0..9).map { i ->
            point(36.0 + i * 0.0002, 3.0, i * 1_000_000_000L, 100.0 + i * 4.0)
        }
        val flatGap = Gap.gapPaceSecPerKm(flat)
        val uphillGap = Gap.gapPaceSecPerKm(uphill)
        assertNotNull(flatGap)
        assertNotNull(uphillGap)
        assertTrue(uphillGap!! > flatGap!!, "uphill $uphillGap vs flat $flatGap")
    }
}
