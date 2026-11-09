package dev.toufik.trailmark.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

private fun stat(
    millis: Long,
    distanceM: Double,
    elapsedNanos: Long = 0L,
    climbM: Double = 0.0,
    sport: String = "",
) = ActivityStat(millis, distanceM, elapsedNanos, climbM, sport)

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
        assertEquals(38, all.size)
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

    @Test
    fun climbAccumulatesAndUnlocksPerSingleEffort() {
        val rows = listOf(
            stat(day(2026, 7, 1), 8_000.0, climbM = 300.0),
            stat(day(2026, 7, 2), 8_000.0, climbM = 300.0),
        )
        val all = evaluateAchievements(rows)
        assertTrue(state(all, "climb_500").unlocked)
        assertFalse(state(all, "climb_1000").unlocked)
        assertTrue(state(all, "climb_single_100").unlocked)
        assertFalse(state(all, "climb_single_500").unlocked)
        assertEquals(600.0, state(all, "climb_1000").current, 1e-9)
    }

    @Test
    fun fasterFiveKUnlocksThePaceTier() {
        // 20 minutes for 5 km = 1200 s; the tier target is 1200 s.
        val all = evaluateAchievements(
            listOf(stat(day(2026, 8, 1), 5_000.0, elapsedNanos = 1_200_000_000_000L)),
        )
        assertTrue(state(all, "pace_5k_20").unlocked)
        assertTrue(state(all, "pace_5k_25").unlocked)
        assertEquals(1_200.0, state(all, "pace_5k_20").raw, 1e-6)
    }

    @Test
    fun slowerFiveKLeavesThePaceTierLockedButShowsTheRealTime() {
        // 30 minutes for 5 km = 1800 s.
        val all = evaluateAchievements(
            listOf(stat(day(2026, 8, 2), 5_000.0, elapsedNanos = 1_800_000_000_000L)),
        )
        assertFalse(state(all, "pace_5k_25").unlocked)
        assertEquals(1_800.0, state(all, "pace_5k_25").raw, 1e-6)
        assertTrue(state(all, "pace_5k_25").fraction() < 1.0)
    }

    @Test
    fun sportsVarietyCountsDistinctDisciplines() {
        val rows = listOf(
            stat(day(2026, 9, 1), 3_000.0, sport = "RUN"),
            stat(day(2026, 9, 2), 3_000.0, sport = "BIKE"),
            stat(day(2026, 9, 3), 3_000.0, sport = "HIKE"),
        )
        val all = evaluateAchievements(rows)
        assertTrue(state(all, "sports_2").unlocked)
        assertTrue(state(all, "sports_3").unlocked)
        assertFalse(state(all, "sports_4").unlocked)
        assertTrue(state(all, "first_bike").unlocked)
        assertTrue(state(all, "first_hike").unlocked)
    }

    @Test
    fun dawnAndNightTiersSplitByStartHour() {
        val rows = listOf(
            stat(day(2026, 10, 1, hour = 5), 2_000.0),
            stat(day(2026, 10, 2, hour = 21), 2_000.0),
        )
        val all = evaluateAchievements(rows)
        assertTrue(state(all, "dawn_1").unlocked)
        assertFalse(state(all, "dawn_10").unlocked)
        assertTrue(state(all, "night_1").unlocked)
        assertFalse(state(all, "night_10").unlocked)
    }

    @Test
    fun familiesGroupEveryTier() {
        val all = evaluateAchievements(emptyList())
        val known = setOf(
            AchFamily.VOLUME, AchFamily.COUNT, AchFamily.SPEED, AchFamily.CLIMB,
            AchFamily.VARIETY, AchFamily.STREAK, AchFamily.TIME,
        )
        assertTrue(all.all { it.family in known })
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }
}
