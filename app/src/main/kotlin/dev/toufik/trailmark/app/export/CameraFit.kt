package dev.toufik.trailmark.app.export

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.sinh

/** Where the camera should sit to show a bounding box. */
data class BoxFit(val lat: Double, val lon: Double, val zoom: Double)

/**
 * Fits a latitude/longitude box into a viewport, the same way MapLibre's
 * `getCameraForLatLngBounds` does: pick the zoom at which the box still fits
 * inside the viewport minus its padding, then centre on the box.
 *
 * Pure maths, so it works in a service with no map instance and can be tested
 * on the JVM.
 */
object CameraFit {
    /** MapLibre renders 512 px tiles. */
    private const val TILE = 512.0
    private const val MIN_ZOOM = 0.0
    private const val MAX_ZOOM = 20.0

    fun fit(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingBottom: Int,
        paddingRight: Int = paddingLeft,
        paddingTop: Int = paddingBottom,
    ): BoxFit {
        val w = (width - paddingLeft - paddingRight).coerceAtLeast(16).toDouble()
        val h = (height - paddingTop - paddingBottom).coerceAtLeast(16).toDouble()

        val x1 = worldX(minLon)
        val x2 = worldX(maxLon)
        val y1 = worldY(maxLat)
        val y2 = worldY(minLat)
        val dx = abs(x2 - x1).coerceAtLeast(1e-12)
        val dy = abs(y2 - y1).coerceAtLeast(1e-12)

        // World size at zoom z is TILE * 2^z pixels.
        val zoomX = log2(w / (TILE * dx))
        val zoomY = log2(h / (TILE * dy))
        val zoom = minOf(zoomX, zoomY).coerceIn(MIN_ZOOM, MAX_ZOOM)

        val centerLat = inverseY((y1 + y2) / 2.0)
        val centerLon = ((minLon + maxLon) / 2.0).coerceIn(-180.0, 180.0)
        return BoxFit(centerLat, centerLon, zoom)
    }

    fun worldX(lon: Double): Double = (lon + 180.0) / 360.0

    fun worldY(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clamped * PI / 180.0
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    }

    fun inverseY(y: Double): Double {
        val n = PI * (1.0 - 2.0 * y)
        return atan(sinh(n)) * 180.0 / PI
    }
}
