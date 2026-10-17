package dev.toufik.trailmark.core.geo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A planned-route vertex with terrain elevation (metres). */
data class ElevPoint(val lat: Double, val lon: Double, val eleM: Double)

/** A contiguous run of a route with a roughly uniform gradient. */
data class RouteChunk(val points: List<ElevPoint>, val gradePercent: Double, val lengthM: Double)

/** Spherical length of a polyline, metres. */
fun polylineLengthMeters(points: List<ElevPoint>): Double {
    var d = 0.0
    for (i in 1 until points.size) {
        d += haversineMeters(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
    }
    return d
}

/**
 * Total climb and descent with ±[hysteresisM] hysteresis so DEM/GPS jitter
 * does not inflate the numbers: a reversal only commits once it exceeds the
 * threshold. Returns (ascentM, descentM), both >= 0.
 */
fun ascentDescent(points: List<ElevPoint>, hysteresisM: Double = 1.0): Pair<Double, Double> {
    if (points.size < 2) return 0.0 to 0.0
    var ascent = 0.0
    var descent = 0.0
    var ref = points.first().eleM
    var extreme = ref
    var goingUp = false
    for (p in points.drop(1)) {
        val e = p.eleM
        if (goingUp) {
            if (e > extreme) {
                extreme = e
            } else if (extreme - e >= hysteresisM) {
                ascent += extreme - ref
                ref = extreme
                goingUp = false
                extreme = e
            }
        } else {
            if (e < extreme) {
                extreme = e
            } else if (e - extreme >= hysteresisM) {
                descent += ref - extreme
                ref = extreme
                goingUp = true
                extreme = e
            }
        }
    }
    if (goingUp) {
        ascent += max(0.0, extreme - ref)
    } else {
        descent += max(0.0, ref - extreme)
    }
    return ascent to descent
}

/**
 * Split a route into chunks of at most [maxChunkM] metres. Consecutive chunks
 * share their boundary vertex so drawn segments connect. Grade of a chunk is
 * (elevation gain / length) × 100, signed.
 */
fun gradeChunks(points: List<ElevPoint>, maxChunkM: Double = 120.0): List<RouteChunk> {
    if (points.size < 2 || maxChunkM <= 0) return emptyList()
    val out = mutableListOf<RouteChunk>()
    var startIdx = 0
    var acc = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        acc += haversineMeters(a.lat, a.lon, b.lat, b.lon)
        if (acc >= maxChunkM || i == points.size - 1) {
            val chunkPts = points.subList(startIdx, i + 1).toList()
            val grade = if (acc > 1e-9) {
                (chunkPts.last().eleM - chunkPts.first().eleM) / acc * 100.0
            } else {
                0.0
            }
            out += RouteChunk(chunkPts, grade, acc)
            startIdx = i
            acc = 0.0
        }
    }
    return out
}

/**
 * Moving-time estimate: flat speed per sport plus a climb penalty per metre
 * climbed. Descending is not credited (conservative, matches most planners).
 */
fun estimateDurationSeconds(
    distanceM: Double,
    ascentM: Double,
    flatSpeedKmh: Double,
    climbPenaltySecPerM: Double,
): Double {
    if (flatSpeedKmh <= 0.0) return 0.0
    val flatSeconds = distanceM / (flatSpeedKmh / 3.6)
    return flatSeconds + max(0.0, ascentM) * clampPenalty(climbPenaltySecPerM)
}

private fun clampPenalty(v: Double): Double = min(60.0, max(0.0, v))

/** Elevation range with a small margin; used by the profile chart. */
fun elevationRangeM(points: List<ElevPoint>, marginM: Double = 5.0): Pair<Double, Double> {
    if (points.isEmpty()) return 0.0 to 0.0
    var lo = points.first().eleM
    var hi = lo
    for (p in points) {
        if (p.eleM < lo) lo = p.eleM
        if (p.eleM > hi) hi = p.eleM
    }
    return (lo - marginM) to (hi + marginM)
}

/** Maximum |grade| over chunks, percent. */
fun maxAbsGradePercent(chunks: List<RouteChunk>): Double {
    var m = 0.0
    for (c in chunks) {
        val a = sqrt(c.gradePercent * c.gradePercent)
        if (a > m) m = a
    }
    return m
}
