package dev.toufik.trailmark.analytics

import java.util.Calendar

/**
 * The minimal per-activity facts the achievement rules read.
 *
 * Only the columns the history list already loads are used: adding a rule never
 * costs an extra query, and an import (which has no climb) still evaluates.
 */
data class ActivityStat(
    val startWallMillis: Long,
    val distanceM: Double,
    val elapsedNanos: Long = 0L,
    val climbM: Double = 0.0,
    val sport: String = "",
)

/** One achievement's live state: where the whole history stands against its tier. */
data class AchievementState(
    val id: String,
    val current: Double,
    val target: Double,
    val unlocked: Boolean,
    val family: String = "volume",
    val lowerIsBetter: Boolean = false,
    val raw: Double = current,
) {
    /** 0..1 for progress bars; a "faster is better" target inverts the ratio. */
    fun fraction(): Double = if (lowerIsBetter) {
        if (current <= 0.0) 0.0 else (target / current).coerceIn(0.0, 1.0)
    } else {
        if (target <= 0.0) 0.0 else (current / target).coerceIn(0.0, 1.0)
    }
}

/** Families are grouped in the UI; the ids are stable so the tests can name them. */
object AchFamily {
    const val VOLUME = "volume"
    const val COUNT = "count"
    const val SPEED = "speed"
    const val CLIMB = "climb"
    const val VARIETY = "variety"
    const val STREAK = "streak"
    const val TIME = "time"
}

private class Summary(val activities: List<ActivityStat>) {
    val count: Double = activities.size.toDouble()
    val totalM: Double = activities.sumOf { it.distanceM }
    val bestM: Double = activities.maxOfOrNull { it.distanceM } ?: 0.0
    val bestStreakDays: Double = bestStreak(activities)
    val totalClimbM: Double = activities.sumOf { it.climbM }
    val bestClimbM: Double = activities.maxOfOrNull { it.climbM } ?: 0.0
    val sports: Set<String> = activities.mapNotNull { it.sport.takeIf { s -> s.isNotBlank() } }.toSet()

    /** Fastest 5 km, extrapolated from any effort of 3 km or more. */
    val best5kSeconds: Double = activities
        .filter { it.distanceM >= 3_000.0 && it.elapsedNanos > 0 }
        .minOfOrNull { it.elapsedNanos / 1e9 * 5_000.0 / it.distanceM }
        ?: 0.0

    val dawnCount: Double = activities.count { hourOf(it.startWallMillis) < 6 }.toDouble()
    val nightCount: Double = activities.count { hourOf(it.startWallMillis) >= 20 }.toDouble()
    val weekendCount: Double = activities.count { isWeekend(it.startWallMillis) }.toDouble()

    fun sportCount(sport: String): Double = activities.count { it.sport == sport }.toDouble()
}

private class Rule(
    val id: String,
    val family: String,
    val target: Double,
    val lowerIsBetter: Boolean = false,
    val measure: (Summary) -> Double,
)

/** Half and full marathon use the official 21.0975 / 42.195 km. */
private val RULES = listOf(
    // Volume and single efforts.
    Rule("first", AchFamily.COUNT, 1.0) { it.count },
    Rule("count_5", AchFamily.COUNT, 5.0) { it.count },
    Rule("count_10", AchFamily.COUNT, 10.0) { it.count },
    Rule("count_25", AchFamily.COUNT, 25.0) { it.count },
    Rule("count_50", AchFamily.COUNT, 50.0) { it.count },
    Rule("count_100", AchFamily.COUNT, 100.0) { it.count },
    Rule("dist_10", AchFamily.VOLUME, 10_000.0) { it.totalM },
    Rule("dist_50", AchFamily.VOLUME, 50_000.0) { it.totalM },
    Rule("dist_100", AchFamily.VOLUME, 100_000.0) { it.totalM },
    Rule("dist_250", AchFamily.VOLUME, 250_000.0) { it.totalM },
    Rule("dist_500", AchFamily.VOLUME, 500_000.0) { it.totalM },
    Rule("dist_1000", AchFamily.VOLUME, 1_000_000.0) { it.totalM },
    Rule("single_5", AchFamily.VOLUME, 5_000.0) { it.bestM },
    Rule("single_10", AchFamily.VOLUME, 10_000.0) { it.bestM },
    Rule("single_21", AchFamily.VOLUME, 21_097.0) { it.bestM },
    Rule("single_42", AchFamily.VOLUME, 42_195.0) { it.bestM },
    // Pace: lower is better, so the measure is the 5 km time in seconds.
    Rule("pace_5k_25", AchFamily.SPEED, 1_500.0, lowerIsBetter = true) { it.best5kSeconds },
    Rule("pace_5k_20", AchFamily.SPEED, 1_200.0, lowerIsBetter = true) { it.best5kSeconds },
    // Climb.
    Rule("climb_500", AchFamily.CLIMB, 500.0) { it.totalClimbM },
    Rule("climb_1000", AchFamily.CLIMB, 1_000.0) { it.totalClimbM },
    Rule("climb_2000", AchFamily.CLIMB, 2_000.0) { it.totalClimbM },
    Rule("climb_5000", AchFamily.CLIMB, 5_000.0) { it.totalClimbM },
    Rule("climb_single_100", AchFamily.CLIMB, 100.0) { it.bestClimbM },
    Rule("climb_single_500", AchFamily.CLIMB, 500.0) { it.bestClimbM },
    // Variety.
    Rule("sports_2", AchFamily.VARIETY, 2.0) { it.sports.size.toDouble() },
    Rule("sports_3", AchFamily.VARIETY, 3.0) { it.sports.size.toDouble() },
    Rule("sports_4", AchFamily.VARIETY, 4.0) { it.sports.size.toDouble() },
    Rule("first_bike", AchFamily.VARIETY, 1.0) { it.sportCount("BIKE") },
    Rule("first_hike", AchFamily.VARIETY, 1.0) { it.sportCount("HIKE") },
    // Streaks.
    Rule("streak_3", AchFamily.STREAK, 3.0) { it.bestStreakDays },
    Rule("streak_7", AchFamily.STREAK, 7.0) { it.bestStreakDays },
    Rule("streak_14", AchFamily.STREAK, 14.0) { it.bestStreakDays },
    Rule("streak_30", AchFamily.STREAK, 30.0) { it.bestStreakDays },
    // When the effort happened.
    Rule("dawn_1", AchFamily.TIME, 1.0) { it.dawnCount },
    Rule("dawn_10", AchFamily.TIME, 10.0) { it.dawnCount },
    Rule("night_1", AchFamily.TIME, 1.0) { it.nightCount },
    Rule("night_10", AchFamily.TIME, 10.0) { it.nightCount },
    Rule("weekend_10", AchFamily.TIME, 10.0) { it.weekendCount },
)

/** Evaluates every tier against the full history; `current` is capped at `target` for display. */
fun evaluateAchievements(activities: List<ActivityStat>): List<AchievementState> {
    val s = Summary(activities)
    return RULES.map { r ->
        val raw = r.measure(s)
        AchievementState(
            id = r.id,
            current = if (r.lowerIsBetter) {
                if (raw <= 0.0) 0.0 else raw.coerceAtLeast(r.target)
            } else {
                raw.coerceAtMost(r.target)
            },
            target = r.target,
            unlocked = if (r.lowerIsBetter) raw > 0.0 && raw <= r.target else raw >= r.target,
            family = r.family,
            lowerIsBetter = r.lowerIsBetter,
            raw = raw,
        )
    }
}

/** Longest run of consecutive calendar days that each hold at least one activity. */
private fun bestStreak(activities: List<ActivityStat>): Double {
    val days = activities.map { dayKey(it.startWallMillis) }.toSortedSet()
    var best = 0
    var run = 0
    var prev = 0L
    for (d in days) {
        run = if (run > 0 && isNextDay(prev, d)) run + 1 else 1
        if (run > best) best = run
        prev = d
    }
    return best.toDouble()
}

private fun hourOf(millis: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    return c.get(Calendar.HOUR_OF_DAY)
}

private fun isWeekend(millis: Long): Boolean {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    val d = c.get(Calendar.DAY_OF_WEEK)
    return d == Calendar.SATURDAY || d == Calendar.SUNDAY
}

/** A calendar day packed as `yyyyMMdd` so numeric order is chronological order. */
private fun dayKey(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    return c.get(Calendar.YEAR) * 10_000L + (c.get(Calendar.MONTH) + 1) * 100L + c.get(Calendar.DAY_OF_MONTH)
}

private fun isNextDay(packed: Long, candidate: Long): Boolean {
    val c = Calendar.getInstance()
    c.clear()
    c.set(
        (packed / 10_000L).toInt(),
        ((packed / 100L) % 100L).toInt() - 1,
        (packed % 100L).toInt(),
    )
    c.add(Calendar.DAY_OF_MONTH, 1)
    val next = c.get(Calendar.YEAR) * 10_000L + (c.get(Calendar.MONTH) + 1) * 100L + c.get(Calendar.DAY_OF_MONTH)
    return next == candidate
}
