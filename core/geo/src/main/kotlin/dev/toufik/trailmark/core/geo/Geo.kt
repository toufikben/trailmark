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
