package dev.toufik.trailmark.core.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationTest {

    private val startLat = 36.75
    private val startLon = 3.18

    @Test
    fun rightAngleProducesOneRightTurn() {
        val pts = leg(startLat, startLon, 0.0, 100.0) + leg(turn.lat, turn.lon, 90.0, 100.0).drop(1)
        val cues = turnCues(pts)
        assertEquals(1, cues.size, cues.toString())
        assertEquals(TurnKind.RIGHT, cues[0].kind)
        assertTrue(abs(cues[0].distanceAlongM - 100.0) < 15.0, "cue at ${cues[0].distanceAlongM}")
        assertEquals(turn.lat, cues[0].lat, 1e-6)
    }

    @Test
    fun gentleBendIsSlight() {
        val pts = leg(startLat, startLon, 0.0, 100.0) + leg(turn.lat, turn.lon, 40.0, 100.0).drop(1)
        val cues = turnCues(pts)
        assertEquals(1, cues.size, cues.toString())
        assertEquals(TurnKind.SLIGHT_RIGHT, cues[0].kind)
    }

    @Test
    fun reversalIsUturn() {
        val pts = leg(startLat, startLon, 0.0, 100.0) + leg(turn.lat, turn.lon, 180.0, 100.0).drop(1)
        val cues = turnCues(pts)
        assertEquals(1, cues.size, cues.toString())
        assertEquals(TurnKind.UTURN, cues[0].kind)
    }

    @Test
    fun straightLineHasNoCues() {
        assertTrue(turnCues(leg(startLat, startLon, 0.0, 300.0)).isEmpty())
    }

    @Test
    fun matchProjectsPositionAndOffset() {
        val pts = leg(startLat, startLon, 0.0, 200.0)
        val mid = destination(startLat, startLon, 0.0, 100.0)
        val side = destination(mid.first, mid.second, 90.0, 30.0)
        val m = matchToRoute(pts, side.first, side.second)!!
        assertTrue(abs(m.distanceAlongM - 100.0) < 3.0, "along ${m.distanceAlongM}")
        assertTrue(abs(m.offRouteM - 30.0) < 3.0, "off ${m.offRouteM}")
    }

    @Test
    fun flattenChunksDropsSharedBoundary() {
        val a = ElevPoint(startLat, startLon, 0.0)
        val b = ElevPoint(startLat + 0.001, startLon, 0.0)
        val c = ElevPoint(startLat + 0.002, startLon, 0.0)
        val chunks = listOf(
            RouteChunk(listOf(a, b), 0.0, 100.0),
            RouteChunk(listOf(b, c), 0.0, 100.0),
        )
        assertEquals(listOf(a, b, c), flattenChunks(chunks))
    }

    private val turn: Pair<Double, Double> by lazy {
        destination(startLat, startLon, 0.0, 100.0)
    }

    private fun leg(lat: Double, lon: Double, bearing: Double, lengthM: Double): List<ElevPoint> {
        val n = 10
        return (0..n).map { i ->
            val p = destination(lat, lon, bearing, lengthM * i / n)
            ElevPoint(p.first, p.second, 0.0)
        }
    }

    private fun destination(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val r = 6_371_008.8
        val d = distanceM / r
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }
}
