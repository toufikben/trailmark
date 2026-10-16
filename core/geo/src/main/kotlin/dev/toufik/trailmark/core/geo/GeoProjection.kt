package dev.toufik.trailmark.core.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Equirectangular projection about the bounds centre (valid for a single
 * route's extent). Longitude is scaled by cos(centreLat) and both axes share
 * one px-per-degree factor, so shapes keep their aspect and one metre is the
 * same pixel count in every direction. A zero-extent route reports no scale.
 */
class GeoProjection private constructor(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    private val widthPx: Double,
    private val heightPx: Double,
    private val padPx: Double,
) {
    val centreLat: Double = (minLat + maxLat) / 2
    val centreLon: Double = (minLon + maxLon) / 2

    val scale: Double = run {
        val w = max(1.0, widthPx - 2 * padPx)
        val h = max(1.0, heightPx - 2 * padPx)
        val xDeg = (maxLon - minLon) * cos(centreLat * PI / 180)
        val dLat = maxLat - minLat
        val sx = if (xDeg > 0) w / xDeg else Double.POSITIVE_INFINITY
        val sy = if (dLat > 0) h / dLat else Double.POSITIVE_INFINITY
        val fit = min(sx, sy)
        if (fit == Double.POSITIVE_INFINITY) 0.0 else fit
    }

    fun x(lon: Double): Double = widthPx / 2 + (lon - centreLon) * cos(centreLat * PI / 180) * scale

    fun y(lat: Double): Double = heightPx / 2 - (lat - centreLat) * scale

    /** Metres per pixel; 0 means "this projection spans nothing, so it has no scale". */
    val metresPerPixel: Double get() = if (scale <= 0) 0.0 else 111320.0 / scale

    companion object {
        fun of(
            lats: List<Double>,
            lons: List<Double>,
            width: Double,
            height: Double,
            pad: Double = 24.0,
        ): GeoProjection? {
            if (lats.isEmpty() || lats.size != lons.size) return null
            return GeoProjection(lats.min(), lats.max(), lons.min(), lons.max(), width, height, pad)
        }
    }
}
