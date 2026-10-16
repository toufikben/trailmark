package dev.toufik.trailmark.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {
    private fun p(t: WaypointType) = StoredPoint(0.0, 0.0, 0L, 5.0, t)

    @Test
    fun displayablePointsDropsRejectedAndKeepsTheRestInOrder() {
        val input = listOf(p(WaypointType.NORMAL), p(WaypointType.REJECTED), p(WaypointType.IDLE))
        assertEquals(
            listOf(WaypointType.NORMAL, WaypointType.IDLE),
            displayablePoints(input).map { it.type },
        )
    }

    @Test
    fun emptyStaysEmpty() {
        assertTrue(displayablePoints(emptyList()).isEmpty())
    }

    @Test
    fun onlyRejectedPairsNothing() {
        assertEquals(0, displayablePoints(listOf(p(WaypointType.REJECTED))).size)
    }
}
