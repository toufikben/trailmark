package dev.toufik.trailmark.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

private fun stat(millis: Long, distanceM: Double) = ActivityStat(millis, distanceM)

private fun state(list: List<AchievementState>, id: String) = list.first { it.id == id }

/** A fixed local-calendar millis so streak tests are timezone-stable. */
private fun day(y: Int, m: Int, d: Int, hour: Int = 12): Long {
    val c = Calendar.getInstance()
    c.clear()
    c.set(y, m - 1, d, hour, 0, 0)
    return c.timeInMillis
}

class AchievementsTest {
    @Test
    fun emptyHistoryUnlocksNothing() {
        val all = evaluateAchievements(emptyList())
        assertEquals(17, all.size)
        assertTrue(all.none { it.unlocked })
        assertEquals(0.0, state(all, "first").current, 1e-9)
    }

    @Test
    fun singleLongRunUnlocksDistanceAndSingleTiers() {
        val all = evaluateAchievements(listOf(stat(day(2026, 10, 1), 12_000.0)))
        assertTrue(state(all, "first").unlocked)
        assertTrue(state(all, "dist_10").unlocked)
        assertTrue(state(all, "single_5").unlocked)
        assertTrue(state(all, "single_10").unlocked)
        assertFalse(state(all, "single_21").unlocked)
        assertFalse(state(all, "dist_50").unlocked)
        assertEquals(12_000.0, state(all, "dist_50").current, 1e-9)
    }

    @Test
    fun cumulativeDistanceAccumulatesAcrossActivities() {
        val rows = (1..6).map { stat(day(2026, 9, it), 20_000.0) }
        val all = evaluateAchievements(rows)
        assertTrue(state(all, "dist_100").unlocked)
        assertFalse(state(all, "dist_250").unlocked)
        assertEquals(120_000.0, state(all, "dist_250").current, 1e-9)
        assertFalse(state(all, "count_10").unlocked)
        assertEquals(6.0, state(all, "count_10").current, 1e-9)
    }

    @Test
    fun oneActivityPerDayStreaksAcrossMonthBoundary() {
        val rows = listOf(
            stat(day(2026, 1, 30), 3_000.0),
            stat(day(2026, 1, 31), 3_000.0),
            stat(day(2026, 2, 1), 3_000.0),
        )
        val all = evaluateAchievements(rows)
        assertTrue(state(all, "streak_3").unlocked)
        assertFalse(state(all, "streak_7").unlocked)
    }

    @Test
    fun gapBreaksTheStreak() {
        val rows = listOf(
            stat(day(2026, 3, 1), 1_000.0),
            stat(day(2026, 3, 3), 1_000.0),
        )
        val all = evaluateAchievements(rows)
        assertFalse(state(all, "streak_3").unlocked)
        assertEquals(1.0, state(all, "streak_3").current, 1e-9)
    }

    @Test
    fun twoActivitiesOnTheSameDayCountAsOneStreakDay() {
        val rows = listOf(
            stat(day(2026, 4, 5, hour = 8), 1_000.0),
            stat(day(2026, 4, 5, hour = 18), 1_000.0),
        )
        val all = evaluateAchievements(rows)
        assertEquals(1.0, state(all, "streak_3").current, 1e-9)
    }

    @Test
    fun exactBoundaryUnlocks() {
        val all = evaluateAchievements(listOf(stat(day(2026, 5, 1), 10_000.0)))
        assertTrue(state(all, "single_10").unlocked)
        assertEquals(10_000.0, state(all, "single_10").current, 1e-9)
    }

    @Test
    fun currentIsCappedAtTargetForDisplay() {
        val all = evaluateAchievements(listOf(stat(day(2026, 6, 1), 60_000.0)))
        assertEquals(10_000.0, state(all, "dist_10").current, 1e-9)
        assertTrue(state(all, "dist_10").unlocked)
    }
}
