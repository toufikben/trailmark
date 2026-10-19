package dev.toufik.trailmark.engine

/** Rough energy cost of an activity, from sport MET values. */
object Calories {

    private fun met(sport: Sport): Double = when (sport) {
        Sport.RUN -> 9.8
        Sport.WALK -> 3.5
        Sport.HIKE -> 6.0
        Sport.BIKE -> 7.5
    }

    /** Estimated kilocalories: MET x body mass (kg) x hours. */
    fun estimateKcal(sport: Sport, seconds: Double, weightKg: Double): Double {
        if (seconds <= 0.0 || weightKg <= 0.0) return 0.0
        return met(sport) * weightKg * (seconds / 3600.0)
    }

    /** Same as [estimateKcal] but from a stored sport code ("RUN", "BIKE", ...). */
    fun estimateKcal(sportCode: String, seconds: Double, weightKg: Double): Double =
        estimateKcal(sportFromCode(sportCode), seconds, weightKg)

    private fun sportFromCode(code: String): Sport = when (code) {
        "WALK" -> Sport.WALK
        "BIKE" -> Sport.BIKE
        "HIKE" -> Sport.HIKE
        else -> Sport.RUN
    }
}
