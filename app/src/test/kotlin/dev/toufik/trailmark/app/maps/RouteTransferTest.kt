package dev.toufik.trailmark.app.maps

import dev.toufik.trailmark.core.geo.ElevPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteTransferTest {
    @Test
    fun importsAKomootStyleTrackWithNameAndClimb() {
        val gpx = """<?xml version="1.0"?><gpx><trk><name>Tour du lac</name><trkseg>
            <trkpt lat="45.000" lon="6.000"><ele>1000</ele></trkpt>
            <trkpt lat="45.010" lon="6.000"><ele>1050</ele></trkpt>
            <trkpt lat="45.020" lon="6.000"><ele>1020</ele></trkpt>
            </trkseg></trk></gpx>"""
        val r = RouteTransfer.parseGpx(gpx.toByteArray(), "fallback", 7L)!!
        assertEquals("Tour du lac", r.name)
        assertEquals(50, r.climbM)
        assertEquals(2.22, r.distanceKm, 0.02)
        assertEquals(3, r.track.size)
        assertEquals(listOf(45.0 to 6.0, 45.02 to 6.0), r.stops)
    }

    @Test
    fun rejectsFilesWithoutAPath() {
        assertNull(RouteTransfer.parseGpx("hello".toByteArray(), "x", 1L))
        assertNull(RouteTransfer.parseGpx("<gpx><trk></trk></gpx>".toByteArray(), "x", 1L))
    }

    @Test
    fun simplifiesHugeTracks() {
        val sb = StringBuilder("<gpx><trk><trkseg>")
        for (i in 0 until 20_000) {
            val lat = 45.0 + i * 0.00001
            val lon = 6.0 + (if (i % 2 == 0) 0.0 else 0.000001)
            sb.append("<trkpt lat=\"").append(lat).append("\" lon=\"").append(lon).append("\"/>")
        }
        sb.append("</trkseg></trk></gpx>")
        // Self-closing trkpt are not track points the reader knows; use the open form.
        val xml = sb.toString().replace("\"/>", "\"></trkpt>")
        val r = RouteTransfer.parseGpx(xml.toByteArray(), "x", 1L)!!
        assertTrue(r.track.size <= 2_000, "kept ${r.track.size}")
    }

    @Test
    fun mapsLinkCarriesStartEndAndAtMostEightStops() {
        val pts = (0 until 50).map { ElevPoint(36.0 + it * 0.001, 3.0, 0.0) }
        val link = RouteTransfer.mapsLink(pts)
        assertTrue(link.startsWith("https://www.google.com/maps/dir/?api=1&origin=36.00000,3.00000"))
        assertTrue(link.contains("&destination=36.04900,3.00000"))
        assertEquals(8, link.substringAfter("&waypoints=").substringBefore("&").split("%7C").size)
    }
}
