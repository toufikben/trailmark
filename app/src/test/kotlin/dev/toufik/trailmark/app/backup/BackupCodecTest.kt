package dev.toufik.trailmark.app.backup

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The backup format is the only thing a user's history survives on, so the
 * encode/decode pair is pinned: every field that the database holds has to come
 * back unchanged, and anything that is not a backup must be refused.
 */
class BackupCodecTest {

    @Test
    fun roundTripKeepsEveryField() {
        val file = BackupFile(
            createdWallMillis = 1_700_000_000_000L,
            activities = listOf(
                BackupActivity(
                    sport = "BIKE",
                    startWallMillis = 1_699_000_000_000L,
                    distanceM = 12_345.6,
                    elapsedNanos = 3_600_000_000_000L,
                    fixCount = 900,
                    rejectedCount = 12,
                    mockDetected = true,
                    weatherTempC = 17.5,
                    weatherWindKmh = 12.0,
                    weatherWindDeg = 240,
                    weatherCode = 3,
                    points = listOf(
                        StoredPoint(
                            lat = 36.5,
                            lon = 2.9,
                            timeNanos = 5_000_000_000L,
                            accuracyM = 4.5,
                            type = WaypointType.IDLE,
                            altitudeM = 210.0,
                            speedMps = 6.1,
                            bearingDeg = 91.0,
                        ),
                        StoredPoint(
                            lat = 36.6,
                            lon = 2.8,
                            timeNanos = 6_000_000_000L,
                            accuracyM = 8.0,
                            type = WaypointType.NORMAL,
                        ),
                    ),
                    media = listOf(
                        BackupMedia(
                            entry = "media/1-2.jpg",
                            kind = "PHOTO",
                            lat = 36.55,
                            lon = 2.85,
                            bearingDeg = 180.0,
                            distanceM = 900.0,
                            elapsedNanos = 600_000_000L,
                            createdWallMillis = 1_699_000_600_000L,
                            durationMs = null,
                            caption = "قمة التل",
                        ),
                    ),
                ),
            ),
        )

        val decoded = assertNotNull(BackupCodec.decode(BackupCodec.encode(file)))
        assertEquals(file.version, decoded.version)
        assertEquals(file.createdWallMillis, decoded.createdWallMillis)
        assertEquals(1, decoded.activities.size)
        val a = decoded.activities.single()
        assertEquals("BIKE", a.sport)
        assertEquals(12_345.6, a.distanceM)
        assertEquals(3_600_000_000_000L, a.elapsedNanos)
        assertEquals(900L, a.fixCount)
        assertEquals(12L, a.rejectedCount)
        assertTrue(a.mockDetected)
        assertEquals(17.5, a.weatherTempC)
        assertEquals(12.0, a.weatherWindKmh)
        assertEquals(240, a.weatherWindDeg)
        assertEquals(3, a.weatherCode)
        assertEquals(2, a.points.size)
        val p = a.points.first()
        assertEquals(36.5, p.lat)
        assertEquals(2.9, p.lon)
        assertEquals(WaypointType.IDLE, p.type)
        assertEquals(210.0, p.altitudeM)
        assertEquals(6.1, p.speedMps)
        assertEquals(91.0, p.bearingDeg)
        assertNull(a.points[1].altitudeM)
        val m = a.media.single()
        assertEquals("media/1-2.jpg", m.entry)
        assertEquals("PHOTO", m.kind)
        assertEquals("قمة التل", m.caption)
        assertNull(m.durationMs)
    }

    @Test
    fun refusesForeignOrNewerFiles() {
        assertNull(BackupCodec.decode("""{"format":"something-else","version":1}"""))
        assertNull(
            BackupCodec.decode(
                """{"format":"trailmark-backup","version":99,"activities":[]}""",
            ),
        )
        assertNull(BackupCodec.decode("not json at all"))
    }

    @Test
    fun emptyBackupIsStillReadable() {
        val decoded = assertNotNull(
            BackupCodec.decode(
                BackupCodec.encode(BackupFile(createdWallMillis = 1L, activities = emptyList())),
            ),
        )
        assertTrue(decoded.activities.isEmpty())
    }
}
