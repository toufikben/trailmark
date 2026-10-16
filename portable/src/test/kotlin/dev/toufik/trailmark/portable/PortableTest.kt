package dev.toufik.trailmark.portable

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun p(lat: Double, lon: Double, t: Long, alt: Double? = null, type: WaypointType = WaypointType.NORMAL) =
    StoredPoint(lat, lon, t, 5.0, type, altitudeM = alt)

class GpxTest {
    @Test
    fun roundTripsThroughWriterAndReader() {
        val pts = listOf(
            p(36.7538, 3.1856, 0L, alt = 10.0),
            p(36.7548, 3.1856, 10_000_000_000L, alt = 12.5),
        )
        val xml = Gpx.write(pts, startWallMillis = 1_700_000_000_000L)
        val back = Gpx.read(xml)
        assertEquals(2, back.size)
        assertEquals(36.7538, back[0].lat, 1e-9)
        assertEquals(3.1856, back[0].lon, 1e-9)
        assertEquals(10.0, back[0].altitudeM!!, 1e-9)
        assertEquals(1_700_000_000_000L, back[0].timeMillis)
        assertEquals(1_700_000_010_000L, back[1].timeMillis)
    }

    @Test
    fun rejectedPointsAreNotWritten() {
        val pts = listOf(p(1.0, 2.0, 0L), p(9.0, 9.0, 1L, type = WaypointType.REJECTED))
        assertEquals(1, Gpx.read(Gpx.write(pts, 0L)).size)
    }

    @Test
    fun missingElevationAndTimeAreNull() {
        val xml = """<gpx><trk><trkseg><trkpt lat="1.5" lon="2.5"></trkpt></trkseg></trk></gpx>"""
        val back = Gpx.read(xml)
        assertEquals(1, back.size)
        assertNull(back[0].altitudeM)
        assertNull(back[0].timeMillis)
    }
}

class TcxTest {
    @Test
    fun writesATrackWithLapTotals() {
        val pts = listOf(
            p(36.7538, 3.1856, 0L, alt = 10.0),
            p(36.7548, 3.1856, 30_000_000_000L, alt = 11.0),
        )
        val xml = Tcx.write(pts, startWallMillis = 1_700_000_000_000L)
        assertTrue(xml.contains("<TrainingCenterDatabase"))
        assertTrue(xml.contains("<TotalTimeSeconds>30</TotalTimeSeconds>"))
        assertTrue(xml.contains("<LatitudeDegrees>36.7538</LatitudeDegrees>"))
    }
}
