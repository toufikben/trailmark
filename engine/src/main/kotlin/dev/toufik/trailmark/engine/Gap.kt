package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType

/**
 * Grade adjusted pace. Uphill running costs more than flat at the same speed,
 * so the GAP model re-prices each segment with the Minetti energy-cost
 * polynomial and reports the flat-equivalent pace in seconds per km.
 */
object Gap {
    // Minetti et al. (2002) cost of locomotion, J/kg/m, grade as fraction.
    fun costFactor(grade: Double): Double {
        val g = grade.coerceIn(-0.5, 0.5)
        val cost = 155.4 * g * g * g * g * g -
            30.4 * g * g * g * g -
            43.3 * g * g * g +
            46.3 * g * g +
            19.6 * g +
            3.6
        return (3.6 / cost.coerceAtLeast(0.1)).coerceIn(0.2, 2.0)
    }

    /**
     * Flat-equivalent pace over the whole path, or null when the track is too
     * short or too few points carry altitude to trust the grade.
     */
    fun gapPaceSecPerKm(points: List<StoredPoint>): Double? {
        var dist = 0.0
        var gapTime = 0.0
        var pairs = 0
        var altPairs = 0
        var prev: StoredPoint? = null
        for (p in points) {
            if (p.type == WaypointType.REJECTED) continue
            val a = prev
            if (a != null) {
                val dt = (p.timeNanos - a.timeNanos) / 1e9
                val d = haversineMeters(a.lat, a.lon, p.lat, p.lon)
                if (d >= 1.0 && dt > 0.0) {
                    pairs++
                    val aa = a.altitudeM
                    val pa = p.altitudeM
                    val grade = if (aa != null && pa != null) {
                        altPairs++
                        (pa - aa) / d
                    } else {
                        0.0
                    }
                    val speed = d / dt
                    val gapSpeed = (speed * costFactor(grade)).coerceAtLeast(0.1)
                    dist += d
                    gapTime += d / gapSpeed
                }
            }
            prev = p
        }
        if (dist < 50.0 || pairs < 3) return null
        if (altPairs * 4 < pairs) return null
        return gapTime / (dist / 1000.0)
    }
}
