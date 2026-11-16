package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GhostMatchTest {
    private val baseLat = 36.7538
    private val baseLon = 3.1856

    private fun point(lat: Double, lon: Double, type: WaypointType = WaypointType.NORMAL) =
        StoredPoint(lat, lon, 0L, 5.0, type)

    private fun east(meters: Double, type: WaypointType = WaypointType.NORMAL): StoredPoint {
        // 1 degree of longitude at this latitude is ~89.2 km.
        return point(baseLat, baseLon + meters / 89_200.0, type)
    }

    private fun north(meters: Double, type: WaypointType = WaypointType.NORMAL): StoredPoint {
        return point(baseLat + meters / 110_540.0, baseLon, type)
    }

    private fun routeEast(meters: Double, steps: Int = 6): List<StoredPoint> =
        (0..steps).map {
            east(meters * it / steps).copy(timeNanos = it * 1_000_000_000L)
        }

    /** East to [meters], then back to the origin: the same ground twice. */
    private fun outAndBack(meters: Double, steps: Int = 10): List<StoredPoint> {
        val out = (0..steps).map { east(meters * it / steps) }
        val back = (1..steps).map { east(meters - meters * it / steps) }
        return (out + back).mapIndexed { i, p -> p.copy(timeNanos = i * 1_000_000_000L) }
    }

    private fun candidate(
        id: Long,
        sport: String = "RUN",
        wall: Long = id,
        points: List<StoredPoint>,
        distanceM: Double = 1000.0,
        elapsedNanos: Long = 300_000_000_000L,
        mockDetected: Boolean = false,
    ) = GhostCandidate(
        activityId = id,
        sport = sport,
        startWallMillis = wall,
        distanceM = distanceM,
        elapsedNanos = elapsedNanos,
        points = points,
        mockDetected = mockDetected,
    )

    @Test
    fun picksTheSameSportAndClosestStart() {
        val candidates = listOf(
            candidate(1, sport = "BIKE", points = routeEast(500.0)),
            candidate(2, points = (0..6).map { east(500.0 + it * 10.0) }),
            candidate(3, points = routeEast(500.0)),
        )

        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 40.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = candidates,
        )

        assertNotNull(match)
        assertEquals(3L, match!!.candidate.activityId)
    }

    @Test
    fun rejectsAStartMoreThanTheConfiguredDistanceAway() {
        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 500.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = routeEast(500.0))),
        )

        assertNull(match)
    }

    @Test
    fun rejectsAHeadingInTheOppositeDirection() {
        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon,
            currentBearing = 270.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = routeEast(500.0))),
        )

        assertNull(match)
    }

    @Test
    fun rejectsARouteThatStartedNearbyThenTurnedAway() {
        val live = routeEast(400.0)
        val turnedAway = listOf(
            east(0.0),
            east(30.0),
            north(100.0),
            north(200.0),
            north(300.0),
        )

        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon,
            currentBearing = null,
            currentRoute = live,
            candidates = listOf(candidate(1, points = turnedAway)),
        )

        assertNull(match)
    }

    @Test
    fun overlapIsSmallOnTheSameLineAndLargeOnAPerpendicularLine() {
        val live = routeEast(400.0)
        val same = routeEast(400.0, steps = 12)
        val perpendicular = (0..6).map { north(it * 50.0) }

        val sameOverlap = routeOverlapM(live, same)
        val otherOverlap = routeOverlapM(live, perpendicular)

        assertNotNull(sameOverlap)
        assertNotNull(otherOverlap)
        assertTrue(sameOverlap!! < 5.0)
        assertTrue(otherOverlap!! > 100.0)
    }

    @Test
    fun rejectedPointsDoNotCreateAFalseOverlap() {
        val live = routeEast(400.0).mapIndexed { i, p ->
            if (i == 2) east(300.0, WaypointType.REJECTED) else p
        }
        val same = routeEast(400.0)

        val overlap = routeOverlapM(live, same)

        assertNotNull(overlap)
        assertTrue(overlap!! < 5.0)
    }

    @Test
    fun joinsTheRouteWhenTheNewRunStartsAfterTheOldOne() {
        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 300.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = routeEast(1000.0, steps = 20))),
        )

        assertNotNull(match)
        assertTrue(match!!.startDistanceM < 5.0)
        assertTrue(match.startOffsetM > 250.0)
        assertTrue(match.remainingM < 760.0)
        assertTrue(match.fromIndex > 0)
    }

    @Test
    fun joinsTheRouteWhenTheNewRunStartsBeforeTheOldOne() {
        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon - 150.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = routeEast(800.0, steps = 16))),
        )

        assertNotNull(match)
        assertEquals(0, match!!.fromIndex)
        assertTrue(match.startDistanceM > 100.0)
        assertTrue(match.remainingM > 700.0)
    }

    @Test
    fun ignoresACandidateWithTooLittleRouteLeft() {
        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 980.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = routeEast(1000.0, steps = 20))),
        )

        assertNull(match)
    }

    @Test
    fun ghostStartedMidRouteCountsFromThatPoint() {
        val points = routeEast(1000.0, steps = 20)
        val full = Ghost(points)
        val joined = Ghost(points, 10)

        assertTrue(joined.lengthM < full.lengthM)
        assertEquals(0.0, joined.distanceAt(0L), 1e-9)
        assertEquals(joined.lengthM, joined.distanceAt(joined.durationNanos), 1e-6)
    }

    // --- joining the right leg of a route that covers the same ground twice ---

    @Test
    fun joinsTheOutboundLegNotTheReturnOne() {
        val loop = outAndBack(500.0, steps = 10)

        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 400.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = loop)),
        )

        assertNotNull(match)
        assertEquals(8, match!!.fromIndex, "the outbound point at 400 m, not the return one")
        assertTrue(match.headingDeltaDeg!! < 5.0)
    }

    @Test
    fun theReturnLegIsRejectedWhenTheRunnerHeadsBack() {
        val loop = outAndBack(500.0, steps = 10)

        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 400.0 / 89_200.0,
            currentBearing = 270.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = loop)),
        )

        assertNotNull(match)
        assertEquals(12, match!!.fromIndex, "the return point at 400 m")
    }

    // --- plausibility filters ---

    @Test
    fun skipsMockedAndTooShortEfforts() {
        val points = routeEast(1000.0, steps = 20)

        assertNull(
            matchGhost(
                sport = "RUN",
                startLat = baseLat,
                startLon = baseLon,
                currentBearing = 90.0,
                currentRoute = emptyList(),
                candidates = listOf(candidate(1, points = points, mockDetected = true)),
            ),
        )
        assertNull(
            matchGhost(
                sport = "RUN",
                startLat = baseLat,
                startLon = baseLon,
                currentBearing = 90.0,
                currentRoute = emptyList(),
                candidates = listOf(candidate(2, points = points, elapsedNanos = 30_000_000_000L)),
            ),
        )
        assertNull(
            matchGhost(
                sport = "RUN",
                startLat = baseLat,
                startLon = baseLon,
                currentBearing = 90.0,
                currentRoute = emptyList(),
                candidates = listOf(candidate(3, points = points, distanceM = 120.0)),
            ),
        )
    }

    @Test
    fun skipsAnEffortOlderThanTheAgeLimit() {
        val now = 1_000_000_000_000L
        val old = now - 200L * 24 * 60 * 60 * 1000
        val points = routeEast(1000.0, steps = 20)

        assertNull(
            matchGhost(
                sport = "RUN",
                startLat = baseLat,
                startLon = baseLon,
                currentBearing = 90.0,
                currentRoute = emptyList(),
                candidates = listOf(candidate(1, wall = old, points = points)),
                nowWallMillis = now,
            ),
        )
        assertNotNull(
            matchGhost(
                sport = "RUN",
                startLat = baseLat,
                startLon = baseLon,
                currentBearing = 90.0,
                currentRoute = emptyList(),
                candidates = listOf(candidate(1, wall = now - 1_000L, points = points)),
                nowWallMillis = now,
            ),
        )
    }

    // --- ranking, so a rejected pick can be replaced ---

    @Test
    fun ranksEveryUsableRivalBestFirst() {
        val near = routeEast(600.0, steps = 12)
        val sameStart = routeEast(600.0, steps = 12)

        val ranked = rankGhosts(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(
                candidate(1, wall = 10L, points = near),
                candidate(2, wall = 20L, points = sameStart),
            ),
        )

        assertEquals(2, ranked.size)
        // Identical geometry: the newer effort is the one to beat.
        assertEquals(2L, ranked.first().candidate.activityId)
        assertTrue(ranked[0].score <= ranked[1].score)
    }

    @Test
    fun aRivalWhoseRouteDivergesIsNotRankedAtAll() {
        val live = routeEast(400.0)
        val turnedAway = listOf(east(0.0), east(30.0), north(120.0), north(240.0), north(360.0))

        val ranked = rankGhosts(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon,
            currentBearing = null,
            currentRoute = live,
            candidates = listOf(candidate(1, points = turnedAway)),
        )

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun theMatchCarriesTheRaceableGhost() {
        val match = matchGhost(
            sport = "RUN",
            startLat = baseLat,
            startLon = baseLon + 300.0 / 89_200.0,
            currentBearing = 90.0,
            currentRoute = emptyList(),
            candidates = listOf(candidate(1, points = routeEast(1000.0, steps = 20))),
        )

        assertNotNull(match)
        val ghost = match!!.ghost
        assertEquals(0.0, ghost.distanceAt(0L), 1e-9)
        assertEquals(match.remainingM, ghost.lengthM, 1e-6)
        assertFalse(ghost.isFinished(0L))
        assertTrue(ghost.isFinished(ghost.durationNanos))
    }
}
