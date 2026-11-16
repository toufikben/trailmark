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

    @Test
    fun readsRoutePointsWhenThereIsNoTrack() {
        val xml = """<gpx><rte><name>Col &amp; lac</name>
            <rtept lat="45.1" lon="6.1"><ele>1200</ele></rtept>
            <rtept lat="45.2" lon="6.2"/>
            </rte></gpx>"""
        val back = Gpx.read(xml)
        assertEquals(2, back.size)
        assertEquals(1200.0, back[0].altitudeM!!, 1e-9)
        assertEquals(6.2, back[1].lon, 1e-9)
        assertEquals("Col & lac", Gpx.name(xml))
    }

    @Test
    fun routeWriterRoundTrips() {
        val pts = listOf(ImportedPoint(36.1, 3.1, 100.0, null), ImportedPoint(36.2, 3.2, null, null))
        val xml = Gpx.writeRoute(pts, "Ridge <loop>")
        assertEquals(pts, Gpx.read(xml))
        assertEquals("Ridge <loop>", Gpx.name(xml))
        assertTrue(xml.contains("<trkpt"))
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

class FitTest {
    private fun le32(bytes: ByteArray, at: Int): Long =
        (0 until 4).sumOf { i -> (bytes[at + i].toLong() and 0xFF) shl (8 * i) }

    @Test
    fun headerAndCrcsAreValid() {
        val pts = listOf(
            p(36.7538, 3.1856, 0L, alt = 10.0),
            p(36.7548, 3.1856, 1_000_000_000L, alt = 11.0),
            p(36.7558, 3.1856, 2_000_000_000L, alt = 12.0),
        )
        val bytes = Fit.write(pts, startWallMillis = 1_700_000_000_000L)
        assertEquals(14, bytes[0].toInt())
        assertEquals(0x20, bytes[1].toInt() and 0xFF)
        assertEquals("FIT", String(bytes, 9, 3))
        assertEquals((bytes.size - 16).toLong(), le32(bytes, 4))
        val headerCrc = (bytes[12].toInt() and 0xFF) or ((bytes[13].toInt() and 0xFF) shl 8)
        assertEquals(Fit.crc16(bytes, 0, 12), headerCrc)
        val fileCrc = (bytes[bytes.size - 2].toInt() and 0xFF) or
            ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
        assertEquals(Fit.crc16(bytes, 0, bytes.size - 2), fileCrc)
    }

    @Test
    fun writesOneRecordPerAcceptedPoint() {
        val pts = listOf(
            p(36.7538, 3.1856, 0L),
            p(36.7548, 3.1856, 1_000_000_000L),
            p(36.7558, 3.1856, 2_000_000_000L),
            p(9.0, 9.0, 3_000_000_000L, type = WaypointType.REJECTED),
        )
        val bytes = Fit.write(pts, startWallMillis = 1_700_000_000_000L)
        val sizes = IntArray(16)
        var records = 0
        var i = 14
        while (i < bytes.size - 2) {
            val header = bytes[i].toInt() and 0xFF
            i++
            val local = header and 0x0F
            if (header and 0x40 != 0) {
                i += 4
                val n = bytes[i].toInt() and 0xFF
                i++
                var size = 0
                repeat(n) {
                    size += bytes[i + 1].toInt() and 0xFF
                    i += 3
                }
                sizes[local] = size
            } else {
                if (local == 1) records++
                i += sizes[local]
            }
        }
        assertEquals(3, records)
        assertEquals(bytes.size - 2, i)
    }

    @Test
    fun roundTripsThroughReader() {
        val pts = listOf(
            p(36.7538, 3.1856, 0L, alt = 10.0),
            p(36.7548, 3.2856, 1_000_000_000L, alt = 12.5),
            p(36.7558, 3.3856, 2_000_000_000L, alt = 15.0),
        )
        val start = 1_700_000_000_000L
        val back = Fit.read(Fit.write(pts, startWallMillis = start))
        assertEquals(3, back.size)
        assertEquals(36.7538, back[0].lat, 1e-6)
        assertEquals(3.1856, back[0].lon, 1e-6)
        assertEquals(10.0, back[0].altitudeM!!, 0.2)
        assertEquals(start, back[0].timeMillis!!)
        assertEquals(start + 1_000L, back[1].timeMillis!!)
        assertEquals(start + 2_000L, back[2].timeMillis!!)
    }

    @Test
    fun aRecordWithTheInvalidCoordinateSentinelIsDropped() {
        // FIT writes 0x7FFFFFFF for a position it could not measure. Read as a
        // semicircle that is a perfectly valid 180°, so one bad record used to
        // drag a whole imported track to the other side of the planet.
        val pts = listOf(
            p(36.7538, 3.1856, 0L, alt = 10.0),
            p(36.7548, 3.1856, 1_000_000_000L, alt = 10.0),
        )
        val bytes = Fit.write(pts, startWallMillis = 1_700_000_000_000L)
        val sizes = IntArray(16)
        var i = 14
        var firstRecord = -1
        while (i < bytes.size - 2) {
            val header = bytes[i].toInt() and 0xFF
            i++
            val local = header and 0x0F
            if (header and 0x40 != 0) {
                i += 4
                val n = bytes[i].toInt() and 0xFF
                i++
                var size = 0
                repeat(n) {
                    size += bytes[i + 1].toInt() and 0xFF
                    i += 3
                }
                sizes[local] = size
            } else {
                if (local == 1 && firstRecord < 0) firstRecord = i
                i += sizes[local]
            }
        }
        // Definition order: timestamp (4), lat (4), lon (4), altitude (2), ...
        val latAt = firstRecord + 4
        bytes[latAt] = 0xFF.toByte()
        bytes[latAt + 1] = 0xFF.toByte()
        bytes[latAt + 2] = 0xFF.toByte()
        bytes[latAt + 3] = 0x7F.toByte()

        val back = Fit.read(bytes)
        assertEquals(1, back.size)
        assertEquals(36.7548, back.single().lat, 1e-6)
    }

    @Test
    fun readerRejectsForeignBytes() {
        assertEquals(0, Fit.read(ByteArray(20)).size)
        assertEquals(0, Fit.read("not a fit file at all".toByteArray()).size)
    }
}
