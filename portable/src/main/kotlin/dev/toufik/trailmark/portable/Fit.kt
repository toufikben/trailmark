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

    /**
     * Reads the record messages of a FIT file (little- or big-endian, plain or
     * compressed timestamp headers). Unknown messages are skipped by size.
     */
    fun read(bytes: ByteArray): List<ImportedPoint> {
        if (bytes.size < 14) return emptyList()
        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize < 12 || bytes.size < headerSize) return emptyList()
        if (
            bytes[8] != '.'.code.toByte() || bytes[9] != 'F'.code.toByte() ||
            bytes[10] != 'I'.code.toByte() || bytes[11] != 'T'.code.toByte()
        ) {
            return emptyList()
        }
        val dataSize = le32(bytes, 4).toInt()
        val end = minOf(bytes.size, headerSize + dataSize)
        val defs = HashMap<Int, MsgDef>()
        val out = ArrayList<ImportedPoint>()
        var lastTimestamp = 0L
        var i = headerSize
        while (i < end) {
            val h = bytes[i].toInt() and 0xFF
            i++
            val compressed = h and 0x80 != 0
            val local = if (compressed) (h ushr 5) and 0x03 else h and 0x0F
            if (!compressed && h and 0x40 != 0) {
                if (i + 5 > end) break
                val arch = bytes[i + 1].toInt() and 0xFF
                val little = arch == 0
                val global = if (little) le16(bytes, i + 2) else be16(bytes, i + 2)
                val n = bytes[i + 4].toInt() and 0xFF
                i += 5
                val fields = ArrayList<FieldDef>(n)
                repeat(n) {
                    if (i + 3 <= end) {
                        fields.add(
                            FieldDef(
                                bytes[i].toInt() and 0xFF,
                                bytes[i + 1].toInt() and 0xFF,
                                bytes[i + 2].toInt() and 0xFF,
                            ),
                        )
                    }
                    i += 3
                }
                defs[local] = MsgDef(global, fields, little)
            } else {
                val def = defs[local] ?: break
                val size = def.fields.sumOf { it.size }
                if (i + size > end) break
                if (def.global == 20) {
                    var off = i
                    var lat: Double? = null
                    var lon: Double? = null
                    var alt: Double? = null
                    for (f in def.fields) {
                        val at = off
                        off += f.size
                        if (compressed && f.num == 253) continue
                        when (f.num) {
                            0 -> if (f.size == 4) lat = deg(readSint32(bytes, at, def.little))
                            1 -> if (f.size == 4) lon = deg(readSint32(bytes, at, def.little))
                            2 -> if (f.size == 2) alt = readU16(bytes, at, def.little) / 5.0 - 500.0
                            253 -> if (f.size == 4) lastTimestamp = readU32(bytes, at, def.little)
                        }
                    }
                    if (compressed) lastTimestamp = (lastTimestamp + (h and 0x1F)) and 0xFFFFFFFFL
                    if (lat != null && lon != null) {
                        out.add(
                            ImportedPoint(
                                lat,
                                lon,
                                alt,
                                (lastTimestamp + FIT_EPOCH_OFFSET_S) * 1000L,
                            ),
                        )
                    }
                }
                i += size
            }
        }
        return out
    }

    private class FieldDef(val num: Int, val size: Int, val type: Int)
    private class MsgDef(val global: Int, val fields: List<FieldDef>, val little: Boolean)

    private fun deg(semicircles: Int): Double = semicircles * (180.0 / Int.MAX_VALUE.toDouble())

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun be16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun le32(b: ByteArray, at: Int): Long =
        (0 until 4).sumOf { i -> (b[at + i].toLong() and 0xFF) shl (8 * i) }

    private fun readU16(b: ByteArray, at: Int, little: Boolean): Int =
        if (little) le16(b, at) else be16(b, at)

    private fun readU32(b: ByteArray, at: Int, little: Boolean): Long {
        var v = 0L
        for (k in 0 until 4) {
            val shift = if (little) 8 * k else 8 * (3 - k)
            v = v or ((b[at + k].toLong() and 0xFF) shl shift)
        }
        return v
    }

    private fun readSint32(b: ByteArray, at: Int, little: Boolean): Int =
        readU32(b, at, little).toInt()

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
