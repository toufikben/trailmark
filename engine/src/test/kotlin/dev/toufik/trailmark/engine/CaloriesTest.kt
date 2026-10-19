package dev.toufik.trailmark.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaloriesTest {

    @Test
    fun hourOfRunningBurnsMetTimesWeight() {
        assertEquals(9.8 * 70.0, Calories.estimateKcal(Sport.RUN, 3600.0, 70.0), 1e-9)
    }

    @Test
    fun halfHourScalesLinearly() {
        assertEquals(
            Calories.estimateKcal(Sport.BIKE, 3600.0, 80.0) / 2.0,
            Calories.estimateKcal(Sport.BIKE, 1800.0, 80.0),
            1e-9,
        )
    }

    @Test
    fun codeOverloadMatchesEnum() {
        assertEquals(
            Calories.estimateKcal(Sport.HIKE, 5400.0, 65.0),
            Calories.estimateKcal("HIKE", 5400.0, 65.0),
            1e-9,
        )
    }

    @Test
    fun nonPositiveInputsYieldZero() {
        assertEquals(0.0, Calories.estimateKcal(Sport.RUN, 0.0, 70.0), 1e-9)
        assertEquals(0.0, Calories.estimateKcal(Sport.RUN, 600.0, 0.0), 1e-9)
        assertTrue(Calories.estimateKcal(Sport.WALK, 600.0, 70.0) > 0.0)
    }
}
