package dev.toufik.trailmark.analytics

import java.util.Calendar

/** The minimal per-activity facts the achievement rules read. */
data class ActivityStat(
    val startWallMillis: Long,
    val distanceM: Double,
)

/** One achievement's live state: where the whole history stands against its tier. */
data class AchievementState(
    val id: String,
    val current: Double,
    val target: Double,
    val unlocked: Boolean,
)

private class Summary(val activities: List<ActivityStat>) {
    val count: Double = activities.size.toDouble()
    val totalM: Double = activities.sumOf { it.distanceM }
    val bestM: Double = activities.maxOfOrNull { it.distanceM } ?: 0.0
    val bestStreakDays: Double = bestStreak(activities)
}

private class Rule(val id: String, val target: Double, val measure: (Summary) -> Double)

/** Half and full marathon use the official 21.0975 / 42.195 km. */
private val RULES = listOf(
    Rule("first", 1.0) { it.count },
    Rule("dist_10", 10_000.0) { it.totalM },
    Rule("dist_50", 50_000.0) { it.totalM },
    Rule("dist_100", 100_000.0) { it.totalM },
    Rule("dist_250", 250_000.0) { it.totalM },
    Rule("dist_500", 500_000.0) { it.totalM },
    Rule("dist_1000", 1_000_000.0) { it.totalM },
    Rule("single_5", 5_000.0) { it.bestM },
    Rule("single_10", 10_000.0) { it.bestM },
    Rule("single_21", 21_097.0) { it.bestM },
    Rule("single_42", 42_195.0) { it.bestM },
    Rule("count_10", 10.0) { it.count },
    Rule("count_25", 25.0) { it.count },
    Rule("count_50", 50.0) { it.count },
    Rule("count_100", 100.0) { it.count },
    Rule("streak_3", 3.0) { it.bestStreakDays },
    Rule("streak_7", 7.0) { it.bestStreakDays },
)

/** Evaluates every tier against the full history; `current` is capped at `target` for display. */
fun evaluateAchievements(activities: List<ActivityStat>): List<AchievementState> {
    val s = Summary(activities)
    return RULES.map { r ->
        val raw = r.measure(s)
        AchievementState(
            id = r.id,
            current = raw.coerceAtMost(r.target),
            target = r.target,
            unlocked = raw >= r.target,
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
