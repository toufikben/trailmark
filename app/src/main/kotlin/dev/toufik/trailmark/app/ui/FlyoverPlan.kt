package dev.toufik.trailmark.app.ui

import dev.toufik.trailmark.core.geo.bearingDeg
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import kotlin.math.roundToInt

/** Frames per second of the exported clip; every frame is rendered. */
const val FLYOVER_FPS = 30

/** Intro (whole route, then down to the start) and outro (back out, stats card). */
const val FLYOVER_INTRO_FRAMES = 2 * FLYOVER_FPS
const val FLYOVER_OUTRO_FRAMES = 3 * FLYOVER_FPS

/** How the camera follows the route. */
enum class FlyoverCamera { FLY_3D, FOLLOW_2D, OVERVIEW }

/** What the user picked before exporting; 0 seconds means "auto". */
data class FlyoverOptions(
    val seconds: Int = 0,
    val camera: FlyoverCamera = FlyoverCamera.FLY_3D,
    val media: dev.toufik.trailmark.app.export.ExportMedia =
        dev.toufik.trailmark.app.export.ExportMedia.OFF,
    val galleryCopy: Boolean = true,
    /** Draw shops, cafés, stations and other points of interest on the map. */
    val pois: Boolean = false,
    /** Draw the line the athlete has already covered, not just the last stretch. */
    val fullTrail: Boolean = true,
    /** Drop onto each photo's spot and climb back out, instead of cutting straight to it. */
    val cinematic: Boolean = true,
)

/** Length of the route part of the clip when the user leaves it on auto. */
fun autoFlyoverSeconds(distanceM: Double): Int =
    (12.0 + distanceM / 1000.0).roundToInt().coerceIn(15, 45)

/** A position along the route. */
class PathSample(val lat: Double, val lon: Double)

/**
 * The route parameterised by distance instead of time, so stops and pauses
 * take no screen time at all: the clip advances only where the athlete moved.
 * Rejected spikes and standing-still points are left out.
 */
class FlyoverPath(points: List<StoredPoint>) {
    private val lats: DoubleArray
    private val lons: DoubleArray
    private val dist: DoubleArray
    private val moving: LongArray

    val totalM: Double
    val movingNanos: Long

    init {
        val kept = points.filter { it.type != WaypointType.REJECTED && it.lat.isFinite() && it.lon.isFinite() }
        val n = kept.size
        lats = DoubleArray(n) { kept[it].lat }
        lons = DoubleArray(n) { kept[it].lon }
        dist = DoubleArray(n)
        moving = LongArray(n)
        for (i in 1 until n) {
            val a = kept[i - 1]
            val b = kept[i]
            dist[i] = dist[i - 1] + haversineMeters(a.lat, a.lon, b.lat, b.lon)
            val dt = b.timeNanos - a.timeNanos
            // Long gaps are pauses; standing still adds no moving time either.
            val counts = dt in 1..MAX_MOVING_STEP_NANOS && b.type != WaypointType.IDLE
            moving[i] = moving[i - 1] + if (counts) dt else 0L
        }
        totalM = if (n == 0) 0.0 else dist[n - 1]
        movingNanos = if (n == 0) 0L else moving[n - 1]
    }

    val isEmpty: Boolean get() = lats.isEmpty()

    fun at(distanceM: Double): PathSample {
        if (lats.size == 1) return PathSample(lats[0], lons[0])
        val (i, f) = locate(distanceM)
        return PathSample(lats[i] + (lats[i + 1] - lats[i]) * f, lons[i] + (lons[i + 1] - lons[i]) * f)
    }

    /** Heading toward a point [lookAheadM] further on, steadier than the instantaneous one. */
    fun bearingAt(distanceM: Double, lookAheadM: Double): Double {
        val d = distanceM.coerceIn(0.0, totalM)
        val from = if (d + lookAheadM <= totalM) d else (totalM - lookAheadM).coerceAtLeast(0.0)
        val a = at(from)
        val b = at((from + lookAheadM).coerceAtMost(totalM))
        return bearingDeg(a.lat, a.lon, b.lat, b.lon)
    }

    /** Moving time elapsed when the athlete had covered [distanceM]. */
    fun movingNanosAt(distanceM: Double): Long {
        if (lats.size < 2) return 0L
        val (i, f) = locate(distanceM)
        return moving[i] + ((moving[i + 1] - moving[i]) * f).toLong()
    }

    /** Average moving speed over the stretch around [distanceM], in m/s. */
    fun speedAt(distanceM: Double, windowM: Double = 120.0): Double {
        val a = (distanceM - windowM / 2).coerceAtLeast(0.0)
        val b = (distanceM + windowM / 2).coerceAtMost(totalM)
        val dt = (movingNanosAt(b) - movingNanosAt(a)) / 1e9
        return if (dt > 0.5) (b - a) / dt else 0.0
    }

    /** Kept points up to [distanceM], ending exactly at the cursor: the travelled line. */
    fun prefix(distanceM: Double): List<PathSample> {
        if (lats.isEmpty()) return emptyList()
        val d = distanceM.coerceIn(0.0, totalM)
        val out = ArrayList<PathSample>()
        for (i in lats.indices) {
            if (dist[i] > d) break
            out.add(PathSample(lats[i], lons[i]))
        }
        out.add(at(d))
        return out
    }

    fun all(): List<PathSample> = lats.indices.map { PathSample(lats[it], lons[it]) }

    private fun locate(distanceM: Double): Pair<Int, Double> {
        val d = distanceM.coerceIn(0.0, totalM)
        var lo = 0
        var hi = dist.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (dist[mid] <= d) lo = mid else hi = mid
        }
        val span = dist[hi] - dist[lo]
        return lo to if (span > 0) (d - dist[lo]) / span else 0.0
    }

    private companion object {
        const val MAX_MOVING_STEP_NANOS = 20_000_000_000L
    }
}

/** Which part of the clip a frame belongs to; [t] runs 0..1 inside that part. */
enum class FlyoverPhase { INTRO, ROUTE, OUTRO }

class FlyoverFrame(val phase: FlyoverPhase, val t: Double, val distanceM: Double)

/**
 * Maps every output frame to what it shows. The route part lasts [routeSeconds]
 * at a constant [FLYOVER_FPS], whatever the activity's length: a long ride gets
 * longer steps per frame, never fewer frames.
 */
class FlyoverTimeline(val totalM: Double, routeSeconds: Int) {
    val routeFrames: Int = (routeSeconds.coerceAtLeast(1) * FLYOVER_FPS).coerceAtLeast(2)
    val frames: Int = FLYOVER_INTRO_FRAMES + routeFrames + FLYOVER_OUTRO_FRAMES

    fun frame(i: Int): FlyoverFrame = when {
        i < FLYOVER_INTRO_FRAMES ->
            FlyoverFrame(FlyoverPhase.INTRO, i.toDouble() / (FLYOVER_INTRO_FRAMES - 1), 0.0)
        i < FLYOVER_INTRO_FRAMES + routeFrames -> {
            val u = (i - FLYOVER_INTRO_FRAMES).toDouble() / (routeFrames - 1)
            FlyoverFrame(FlyoverPhase.ROUTE, u, totalM * routeEase(u))
        }
        else -> {
            val k = i - FLYOVER_INTRO_FRAMES - routeFrames
            FlyoverFrame(FlyoverPhase.OUTRO, k.toDouble() / (FLYOVER_OUTRO_FRAMES - 1), totalM)
        }
    }

    fun ptsUs(i: Int): Long = i * 1_000_000L / FLYOVER_FPS

    val durationNanos: Long get() = frames * 1_000_000_000L / FLYOVER_FPS
}

/** Smooth step, for camera moves. */
fun easeInOut(x: Double): Double {
    val c = x.coerceIn(0.0, 1.0)
    return c * c * (3 - 2 * c)
}

/** Constant pace through the middle, gently easing in and out at the ends. */
internal fun routeEase(u: Double): Double = 0.8 * u.coerceIn(0.0, 1.0) + 0.2 * easeInOut(u)

/** Zoom that keeps a few seconds of route ahead on screen at the athlete's average speed. */
fun followZoom(avgSpeedMps: Double, tilted: Boolean): Double {
    val kmh = avgSpeedMps * 3.6
    val base = when {
        kmh < 8 -> 16.2
        kmh < 20 -> 15.6
        kmh < 40 -> 15.0
        else -> 14.4
    }
    return if (tilted) base else base + 0.3
}
