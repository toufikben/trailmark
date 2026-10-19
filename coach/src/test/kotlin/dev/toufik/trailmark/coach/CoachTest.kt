package dev.toufik.trailmark.coach

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachTest {
    @Test
    fun previousBestFillsDistanceAndTimeInArabic() {
        val msg = Coach.message(
            CoachEvent.PreviousBest(distanceM = 10_000.0, seconds = 3130, targetM = 10_000.0),
            Locale.AR,
            seed = 0,
        )
        assertTrue(msg.contains("10"), msg)
        assertTrue(msg.contains("52:10"), msg)
    }

    @Test
    fun localeSelectsTheLanguage() {
        val ev = CoachEvent.Ahead(seconds = 12)
        assertTrue(Coach.message(ev, Locale.AR).contains("12"))
        assertTrue(Coach.message(ev, Locale.FR).contains("12"))
        assertTrue(Coach.message(ev, Locale.EN).contains("12"))
        assertNotEquals(Coach.message(ev, Locale.FR), Coach.message(ev, Locale.EN))
    }

    @Test
    fun seedPicksDifferentVariants() {
        val ev = CoachEvent.Milestone(km = 5)
        assertNotEquals(Coach.message(ev, Locale.EN, seed = 0), Coach.message(ev, Locale.EN, seed = 1))
    }

    @Test
    fun seedWrapsAndStaysInRange() {
        // 17 variants of 2 modulo 2 is 1 — never an index error.
        val msg = Coach.message(CoachEvent.Milestone(km = 3), Locale.EN, seed = 17)
        assertTrue(msg.contains("3"))
    }

    @Test
    fun behindIncludesSeconds() {
        assertEquals(true, Coach.message(CoachEvent.Behind(8), Locale.EN, 0).contains("8"))
    }

    @Test
    fun segmentStartNamesTheSegment() {
        assertTrue(Coach.message(CoachEvent.SegmentStart("Mont Bouzareah"), Locale.EN, 0).contains("Mont Bouzareah"))
        assertTrue(Coach.message(CoachEvent.SegmentStart("Mont Bouzareah"), Locale.AR, 0).contains("Mont Bouzareah"))
        assertTrue(Coach.message(CoachEvent.SegmentStart("Mont Bouzareah"), Locale.FR, 1).contains("Mont Bouzareah"))
    }

    @Test
    fun segmentFinishNamesSegmentAndTime() {
        val en = Coach.message(CoachEvent.SegmentFinish("Mont Bouzareah", 754), Locale.EN, 0)
        assertTrue(en.contains("Mont Bouzareah"))
        assertTrue(en.contains("12:34"))
        val ar = Coach.message(CoachEvent.SegmentFinish("Mont Bouzareah", 65), Locale.AR, 0)
        assertTrue(ar.contains("Mont Bouzareah"))
        assertTrue(ar.contains("1:05"))
    }

    @Test
    fun turnAnnouncesDirectionAndDistance() {
        val en = Coach.message(CoachEvent.Turn(TurnDirection.RIGHT, 120), Locale.EN, 0)
        assertTrue(en.contains("right"), en)
        assertTrue(en.contains("120"), en)
        val ar = Coach.message(CoachEvent.Turn(TurnDirection.LEFT, 50), Locale.AR, 0)
        assertTrue(ar.contains("يساراً"), ar)
        assertTrue(ar.contains("50"), ar)
        val fr = Coach.message(CoachEvent.Turn(TurnDirection.UTURN, 30), Locale.FR, 0)
        assertTrue(fr.contains("demi-tour"), fr)
    }

    @Test
    fun offRouteDoesNotMentionDistance() {
        val en = Coach.message(CoachEvent.OffRoute, Locale.EN, 0)
        assertTrue(en.contains("off route"), en)
        assertTrue(Coach.message(CoachEvent.OffRoute, Locale.AR, 1).isNotEmpty())
    }
}
