package dev.toufik.trailmark.core.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 mean radius (IUGG). */
private const val EARTH_RADIUS_M = 6_371_008.8

private fun rad(deg: Double): Double = deg * PI / 180.0

/**
 * Spherical distance in metres. On segments <= 25 m the difference from a
 * full ellipsoidal geodesic is centimetres — far below GPS noise.
 */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = rad(lat1)
    val phi2 = rad(lat2)
    val dPhi = phi2 - phi1
    val dLon = rad(lon2 - lon1)
    val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
}

/** Compass bearing in degrees [0, 360) from point 1 to point 2. */
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = rad(lat1)
    val phi2 = rad(lat2)
    val dLon = rad(lon2 - lon1)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (atan2(y, x) * 180 / PI + 360) % 360
}

/**
 * Douglas-Peucker simplification for display: keeps the endpoints and every
 * point whose perpendicular distance to the retained chord exceeds
 * [toleranceM]. Distances use an equirectangular projection around the middle
 * latitude — accurate to well under the tolerance at city scale.
 */
fun <T> douglasPeucker(
    points: List<T>,
    toleranceM: Double,
    lat: (T) -> Double,
    lon: (T) -> Double,
): List<T> {
    if (points.size <= 2) return points
    val midLat = lat(points[points.size / 2])
    val mx = 111_320.0 * cos(rad(midLat))
    val my = 110_540.0
    fun x(p: T): Double = lon(p) * mx
    fun y(p: T): Double = lat(p) * my
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, points.size - 1))
    while (stack.isNotEmpty()) {
        val range = stack.removeLast()
        val s = range[0]
        val e = range[1]
        if (e <= s + 1) continue
        val x1 = x(points[s])
        val y1 = y(points[s])
        val x2 = x(points[e])
        val y2 = y(points[e])
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        var maxD = -1.0
        var maxI = -1
        for (i in s + 1 until e) {
            val px = x(points[i])
            val py = y(points[i])
            val d = if (len == 0.0) {
                sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
            } else {
                kotlin.math.abs(dy * px - dx * py + x2 * y1 - y2 * x1) / len
            }
            if (d > maxD) {
                maxD = d
                maxI = i
            }
        }
        if (maxD > toleranceM && maxI > s) {
            keep[maxI] = true
            stack.addLast(intArrayOf(s, maxI))
            stack.addLast(intArrayOf(maxI, e))
        }
    }
    val out = ArrayList<T>(points.size / 2 + 2)
    points.forEachIndexed { i, p -> if (keep[i]) out.add(p) }
    return out
}
