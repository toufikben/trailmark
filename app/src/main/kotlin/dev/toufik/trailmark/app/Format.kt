package dev.toufik.trailmark.app

import java.util.Calendar

/** Metres of accumulated distance as `km,ss`. */
fun formatDistance(metres: Double): String {
    val km = metres / 1000.0
    val whole = km.toLong()
    val frac = Math.round((km - whole) * 100)
    val f = if (frac == 100L) 0L else frac
    val w = if (frac == 100L) whole + 1 else whole
    return "$w,${f.toString().padStart(2, '0')}"
}

/** Elapsed monotonic nanos as `mm:ss` (adding `hh:` past an hour). */
fun formatDuration(nanos: Long): String {
    val s = nanos / 1_000_000_000L
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun p(n: Long) = n.toString().padStart(2, '0')
    return if (h == 0L) "${p(m)}:${p(sec)}" else "${p(h)}:${p(m)}:${p(sec)}"
}

/** Seconds-per-kilometre as `m:ss`; out-of-range renders `--:--`. */
fun formatPace(secPerKm: Double): String {
    if (secPerKm.isNaN() || secPerKm <= 0.0 || secPerKm > 3600.0) return "--:--"
    val s = secPerKm.toLong()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private val MONTHS_AR = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
)

/** A start stamp as `15 أكتوبر 2026 · 21:33`, or اليوم / أمس for the two most recent calendar days. */
fun formatDate(millis: Long, now: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = c.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val dayGap = n.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) +
        if (sameYear) 0 else 365 * (n.get(Calendar.YEAR) - c.get(Calendar.YEAR))
    val whenWord = when (dayGap) {
        0 -> "اليوم"
        1 -> "أمس"
        else -> "${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS_AR[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
    }
    val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$whenWord · $hh:$mm"
}
