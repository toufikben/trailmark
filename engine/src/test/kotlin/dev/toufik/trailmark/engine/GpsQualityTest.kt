package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.RawFix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun f(acc: Double, sec: Long = 0) = RawFix(1.0, 1.0, sec * 1_000_000_000L, acc)

class SportConfigTest {
    private val cfg = SportConfig(Sport.RUN, maxSpeedMps = 6.0)

    @Test
    fun gateFloorsAtThreeMetres() = assertEquals(3.0, cfg.gateFor(0.5), 1e-9)

    @Test
    fun gateIsTwiceAccuracy() = assertEquals(8.0, cfg.gateFor(4.0), 1e-9)

    @Test
    fun gateSaturatesAtOldCapUnchanged() = assertEquals(25.0, cfg.gateFor(12.5), 1e-9)

    @Test
    fun gateHasNoCeiling() = assertEquals(198.0, cfg.gateFor(99.0), 1e-9)

    @Test
    fun gateIsInfiniteForUnmeasuredAccuracy() =
        assertTrue(cfg.gateFor(UNMEASURED_ACCURACY_M).isInfinite())
}

class GpsQualityTest {
    @Test
    fun tiers() {
        assertEquals(GpsQuality.OK, GpsQualityX.fromAccuracy(20.0))
        assertEquals(GpsQuality.DEGRADED, GpsQualityX.fromAccuracy(20.1))
        assertEquals(GpsQuality.DEGRADED, GpsQualityX.fromAccuracy(35.0))
        assertEquals(GpsQuality.SEARCHING, GpsQualityX.fromAccuracy(35.1))
        assertTrue(isFixGoodEnough(f(35.0)))
        assertFalse(isFixGoodEnough(f(35.1)))
    }

    @Test
    fun gateIsShutBeforeAnyFix() {
        val gate = FixQualityGate { 0L }
        assertEquals(GpsQuality.NONE, gate.quality)
        assertFalse(gate.ready)
    }

    @Test
    fun twoConsecutiveGoodFixesOpenTheGate() {
        val gate = FixQualityGate { 0L }
        assertFalse(gate.add(f(12.0)))
        assertEquals(GpsQuality.OK, gate.quality)
        assertTrue(gate.add(f(30.0)))
        assertEquals(GpsQuality.DEGRADED, gate.quality)
        assertTrue(gate.add(f(40.0)))
        assertEquals(GpsQuality.SEARCHING, gate.quality)
        assertTrue(gate.ready, "ر7 is a start gate, not a licence")
    }

    @Test
    fun aBadAccuracyRestartsTheStreak() {
        val gate = FixQualityGate { 0L }
        assertFalse(gate.add(f(10.0)))
        assertFalse(gate.add(f(80.0)))
        assertFalse(gate.add(f(10.0)))
        assertTrue(gate.add(f(10.0)))
    }

    @Test
    fun unmeasuredAccuracyNeverOpensTheGate() {
        val gate = FixQualityGate { 0L }
        assertFalse(gate.add(f(UNMEASURED_ACCURACY_M)))
        assertFalse(gate.add(f(UNMEASURED_ACCURACY_M)))
        assertEquals(GpsQuality.SEARCHING, gate.quality)
    }

    @Test
    fun twentySecondsOfSilenceIsLost() {
        var now = 0L
        val gate = FixQualityGate { now }
        gate.add(f(4.0))
        gate.add(f(4.0))
        assertEquals(GpsQuality.OK, gate.quality)
        now = 19L * 1_000_000_000L
        assertEquals(GpsQuality.OK, gate.quality)
        now = 20L * 1_000_000_000L
        assertEquals(GpsQuality.LOST, gate.quality)
        now = 25L * 1_000_000_000L
        gate.add(f(4.0))
        assertEquals(GpsQuality.OK, gate.quality)
        assertTrue(gate.ready)
    }

    @Test
    fun unmeasuredSentinelIsNotZero() {
        assertNotEquals(0.0, UNMEASURED_ACCURACY_M)
        assertTrue(UNMEASURED_ACCURACY_M.isInfinite())
    }
}
