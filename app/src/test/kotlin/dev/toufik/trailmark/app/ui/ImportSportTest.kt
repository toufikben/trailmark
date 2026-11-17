package dev.toufik.trailmark.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportSportTest {

    @Test
    fun theHintAFileCarriesPicksTheSport() {
        // Imports used to land as RUN no matter what the file said, so every
        // ride and hike polluted the run records and run-only splits.
        assertEquals("BIKE", sportHintToSport("cycling"))
        assertEquals("BIKE", sportHintToSport("E-Bike"))
        assertEquals("BIKE", sportHintToSport("biking"))
        assertEquals("HIKE", sportHintToSport("hiking"))
        assertEquals("HIKE", sportHintToSport("Wanderung"))
        assertEquals("WALK", sportHintToSport("walking"))
        assertEquals("WALK", sportHintToSport("marche"))
        assertEquals("RUN", sportHintToSport("Running"))
        assertEquals("RUN", sportHintToSport("trail running"))
    }

    @Test
    fun anAbsentOrUnknownHintStaysARun() {
        assertEquals("RUN", sportHintToSport(null))
        assertEquals("RUN", sportHintToSport(""))
        assertEquals("RUN", sportHintToSport("kayaking"))
    }
}
