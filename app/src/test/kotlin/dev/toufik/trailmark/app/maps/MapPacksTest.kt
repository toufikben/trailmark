package dev.toufik.trailmark.app.maps

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class MapPacksTest {

    private fun header(
        version: Int = 3,
        minLon: Int = 27_000_000,
        minLat: Int = 364_000_000,
        maxLon: Int = 40_000_000,
        maxLat: Int = 368_000_000,
    ): ByteArray {
        val h = ByteArray(127)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(h)
        h[7] = version.toByte()
        fun put(at: Int, v: Int) {
            h[at] = v.toByte()
            h[at + 1] = (v shr 8).toByte()
            h[at + 2] = (v shr 16).toByte()
            h[at + 3] = (v shr 24).toByte()
        }
        put(102, minLon)
        put(106, minLat)
        put(110, maxLon)
        put(114, maxLat)
        return h
    }

    @Test
    fun boundsReadFromPmtilesHeader() {
        val f = File.createTempFile("pack", ".map.pack")
        try {
            f.writeBytes(header())
            val b = MapPacks.packBounds(f)!!
            assertArrayEquals(doubleArrayOf(2.7, 36.4, 4.0, 36.8), b, 1e-9)
        } finally {
            f.delete()
        }
    }

    @Test
    fun rejectsNonPmtiles() {
        val f = File.createTempFile("pack", ".map.pack")
        try {
            f.writeBytes(ByteArray(127))
            assertNull(MapPacks.packBounds(f))
        } finally {
            f.delete()
        }
    }

    @Test
    fun rejectsOlderVersion() {
        val f = File.createTempFile("pack", ".map.pack")
        try {
            f.writeBytes(header(version = 2))
            assertNull(MapPacks.packBounds(f))
        } finally {
            f.delete()
        }
    }
}
