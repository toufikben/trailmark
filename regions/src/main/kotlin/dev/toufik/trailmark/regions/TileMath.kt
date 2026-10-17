package dev.toufik.trailmark.regions

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.tan

/** Pure math for offline tile areas: a bbox around a point and its tile budget. */
object TileMath {

    const val EARTH_RADIUS_KM = 6371.0088

    /** Kilometres per degree of latitude (spherical approximation). */
    const val KM_PER_DEGREE = EARTH_RADIUS_KM * PI / 180.0

    /** A bbox around lat/lon as west,south,east,north in degrees. */
    fun bboxAround(lat: Double, lon: Double, radiusKm: Double): DoubleArray {
        val angular = radiusKm / KM_PER_DEGREE
        val dLat = angular
        val dLon = angular / cos(Math.toRadians(lat)).coerceAtLeast(1.0e-3)
        return doubleArrayOf(lon - dLon, lat - dLat, lon + dLon, lat + dLat)
    }

    fun tileX(lon: Double, zoom: Int): Long {
        val n = 1L shl zoom
        return ((lon + 180.0) / 360.0 * n).toLong().coerceIn(0L, n - 1)
    }
    fun tileY(lat: Double, zoom: Int): Long {
        val n = 1L shl zoom
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val y = (1.0 - asinh(tan(latRad)) / PI) / 2.0 * n
        return y.toLong().coerceIn(0L, n - 1)
    }

    /** Number of WMTS tiles covering the bbox across minZoom..maxZoom. */
    fun tileCount(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        var total = 0L
        for (z in minZoom..maxZoom) {
            val x0 = tileX(west, z)
            val x1 = tileX(east, z)
            val y0 = tileY(north, z)
            val y1 = tileY(south, z)
            total += (x1 - x0 + 1) * (y1 - y0 + 1)
        }
        return total
    }
}
