package dev.toufik.trailmark.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Calendar

class FormatTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun distanceFormatsWithCommaAndCarry() {
        assertEquals("0,00", formatDistance(0.0))
        assertEquals("1,23", formatDistance(1234.0))
        assertEquals("0,99", formatDistance(994.0))
        assertEquals("100,00", formatDistance(99_999.0))
    }

    @Test
    fun durationAddsHoursPastAnHour() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:59", formatDuration(59_000_000_000L))
        assertEquals("01:00:00", formatDuration(3_600_000_000_000L))
        assertEquals("02:03:04", formatDuration(7_384_000_000_000L))
    }

    @Test
    fun paceRejectsOutOfRange() {
        assertEquals("--:--", formatPace(Double.NaN))
        assertEquals("--:--", formatPace(0.0))
        assertEquals("--:--", formatPace(-1.0))
        assertEquals("--:--", formatPace(3600.5))
        assertEquals("5:30", formatPace(330.0))
        assertEquals("0:59", formatPace(59.9))
    }

    @Test
    fun dateUsesTodayAndYesterdayWords() {
        val now = at(2026, Calendar.OCTOBER, 15, 23, 0)
        val earlierToday = at(2026, Calendar.OCTOBER, 15, 21, 30)
        val yesterday = at(2026, Calendar.OCTOBER, 14, 9, 5)

        assertEquals("اليوم · 21:30", formatDate(earlierToday, Lang.AR, now))
        assertEquals("Today · 21:30", formatDate(earlierToday, Lang.EN, now))
        assertEquals("أمس · 09:05", formatDate(yesterday, Lang.AR, now))
        assertEquals("Yesterday · 09:05", formatDate(yesterday, Lang.EN, now))
    }

    @Test
    fun dateFallsBackToFullDateForOlderDays() {
        val now = at(2026, Calendar.OCTOBER, 15, 23, 0)
        val old = at(2026, Calendar.SEPTEMBER, 3, 8, 7)
        assertEquals("3 September 2026 · 08:07", formatDate(old, Lang.EN, now))
    }
}
