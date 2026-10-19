package dev.toufik.trailmark.portable

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * FIT (Flexible and Interoperable Data Transfer) activity writer. Emits the
 * minimum an import tool needs: file_id, one record per fix, lap, session and
 * activity messages, little-endian, with the header and file CRC-16 filled in.
 */
object Fit {
    private const val FIT_EPOCH_OFFSET_S = 631_065_600L
    private const val PROFILE_VERSION = 2160

    private const val ENUM = 0x00
    private const val UINT16 = 0x84
    private const val SINT32 = 0x85
    private const val UINT32 = 0x86
    private const val UINT32Z = 0x8C

    fun write(points: List<StoredPoint>, startWallMillis: Long, sport: String = "Running"): ByteArray {
        val route = points.filter { it.type != WaypointType.REJECTED }
        val t0 = route.firstOrNull()?.timeNanos ?: 0L
        val tLast = route.lastOrNull()?.timeNanos ?: 0L
        val elapsedMs = ((tLast - t0) / 1_000_000L).coerceAtLeast(0L)
        val startFit = fitSeconds(startWallMillis)
        val endFit = fitSeconds(startWallMillis + elapsedMs)

        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) {
            cum[i] = cum[i - 1] + haversineMeters(
                route[i - 1].lat, route[i - 1].lon, route[i].lat, route[i].lon,
            )
        }
        val totalDistance = cum.lastOrNull() ?: 0.0

        val body = ByteArrayOutputStream()
        val out = DataOutputStream(body)

        out.definition(
            0, 0,
            listOf(
                Triple(0, 1, ENUM),
                Triple(1, 2, UINT16),
                Triple(2, 2, UINT16),
                Triple(3, 4, UINT32Z),
                Triple(4, 4, UINT32),
            ),
        )
        out.u8(0x00)
        out.u8(4)
        out.u16(255)
        out.u16(1)
        out.u32(0x12345678L)
        out.u32(startFit)

        out.definition(
            1, 20,
            listOf(
                Triple(253, 4, UINT32),
                Triple(0, 4, SINT32),
                Triple(1, 4, SINT32),
                Triple(2, 2, UINT16),
                Triple(5, 4, UINT32),
                Triple(6, 2, UINT16),
            ),
        )
        route.forEachIndexed { i, p ->
            out.u8(0x01)
            out.u32(fitSeconds(startWallMillis + (p.timeNanos - t0) / 1_000_000L))
            out.semicircle(p.lat)
            out.semicircle(p.lon)
            out.u16((((p.altitudeM ?: 0.0) + 500.0) * 5.0).toInt().coerceIn(0, 65535))
            out.u32((cum[i] * 100.0).toLong().coerceIn(0L, 0xFFFFFFFFL))
            out.u16(((p.speedMps ?: 0.0) * 1000.0).toInt().coerceIn(0, 65535))
        }

        out.definition(
            2, 19,
            listOf(
                Triple(253, 4, UINT32),
                Triple(2, 4, UINT32),
                Triple(3, 4, SINT32),
                Triple(4, 4, SINT32),
                Triple(5, 4, SINT32),
                Triple(6, 4, SINT32),
                Triple(7, 4, UINT32),
                Triple(8, 4, UINT32),
                Triple(9, 4, UINT32),
            ),
        )
        out.u8(0x02)
        out.u32(endFit)
        out.u32(startFit)
        out.semicircle(route.firstOrNull()?.lat ?: 0.0)
        out.semicircle(route.firstOrNull()?.lon ?: 0.0)
        out.semicircle(route.lastOrNull()?.lat ?: 0.0)
        out.semicircle(route.lastOrNull()?.lon ?: 0.0)
        out.u32(elapsedMs)
        out.u32(elapsedMs)
        out.u32((totalDistance * 100.0).toLong().coerceIn(0L, 0xFFFFFFFFL))

        out.definition(
            3, 18,
            listOf(
                Triple(253, 4, UINT32),
                Triple(2, 4, UINT32),
                Triple(3, 4, SINT32),
                Triple(4, 4, SINT32),
                Triple(5, 1, ENUM),
                Triple(7, 4, UINT32),
                Triple(8, 4, UINT32),
                Triple(9, 4, UINT32),
            ),
        )
        out.u8(0x03)
        out.u32(endFit)
        out.u32(startFit)
        out.semicircle(route.firstOrNull()?.lat ?: 0.0)
        out.semicircle(route.firstOrNull()?.lon ?: 0.0)
        out.u8(sportId(sport))
        out.u32(elapsedMs)
        out.u32(elapsedMs)
        out.u32((totalDistance * 100.0).toLong().coerceIn(0L, 0xFFFFFFFFL))

        out.definition(
            4, 34,
            listOf(
                Triple(253, 4, UINT32),
                Triple(0, 4, UINT32),
                Triple(1, 2, UINT16),
                Triple(2, 1, ENUM),
                Triple(3, 1, ENUM),
                Triple(4, 1, ENUM),
            ),
        )
        out.u8(0x04)
        out.u32(endFit)
        out.u32(elapsedMs)
        out.u16(1)
        out.u8(0)
        out.u8(26)
        out.u8(1)

        out.flush()
        val data = body.toByteArray()

        val header = ByteArray(14)
        header[0] = 14
        header[1] = 0x20
        header[2] = (PROFILE_VERSION and 0xFF).toByte()
        header[3] = ((PROFILE_VERSION ushr 8) and 0xFF).toByte()
        header[4] = (data.size and 0xFF).toByte()
        header[5] = ((data.size ushr 8) and 0xFF).toByte()
        header[6] = ((data.size ushr 16) and 0xFF).toByte()
        header[7] = ((data.size ushr 24) and 0xFF).toByte()
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()
        val headerCrc = crc16(header, 0, 12)
        header[12] = (headerCrc and 0xFF).toByte()
        header[13] = ((headerCrc ushr 8) and 0xFF).toByte()

        val all = header + data
        val fileCrc = crc16(all, 0, all.size)
        return all + byteArrayOf(
            (fileCrc and 0xFF).toByte(),
            ((fileCrc ushr 8) and 0xFF).toByte(),
        )
    }

    internal fun crc16(bytes: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    private fun fitSeconds(unixMillis: Long): Long =
        (unixMillis / 1000L - FIT_EPOCH_OFFSET_S).coerceIn(0L, 0xFFFFFFFFL)

    private fun sportId(sport: String): Int = when (sport.lowercase()) {
        "running", "run" -> 1
        "cycling", "biking", "ride" -> 2
        "walking", "walk" -> 11
        "hiking", "hike" -> 17
        else -> 0
    }

    private fun DataOutputStream.definition(local: Int, global: Int, fields: List<Triple<Int, Int, Int>>) {
        u8(0x40 or local)
        u8(0)
        u8(0)
        u16(global)
        u8(fields.size)
        fields.forEach { (num, size, type) ->
            u8(num)
            u8(size)
            u8(type)
        }
    }

    private fun DataOutputStream.semicircle(deg: Double) {
        u32((deg * (Int.MAX_VALUE / 180.0)).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()) and 0xFFFFFFFFL)
    }

    private fun DataOutputStream.u8(v: Int) {
        writeByte(v and 0xFF)
    }

    private fun DataOutputStream.u16(v: Int) {
        u8(v and 0xFF)
        u8((v ushr 8) and 0xFF)
    }

    private fun DataOutputStream.u32(v: Long) {
        repeat(4) { i -> u8(((v ushr (8 * i)) and 0xFF).toInt()) }
    }
}
