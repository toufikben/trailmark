package dev.toufik.trailmark.app

import java.util.Calendar

private const val METRES_PER_MILE = 1609.344
private const val KM_PER_MILE = 1.609344
private const val MPS_TO_MPH = 2.2369362920544
private const val METRES_TO_FEET = 3.2808398950131

/** Distance in metres as `w,ff` in the active unit (kilometres or miles). */
fun formatDistance(metres: Double): String {
    val value = if (Units.imperial) metres / METRES_PER_MILE else metres / 1000.0
    val whole = value.toLong()
    val frac = Math.round((value - whole) * 100)
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

/** Seconds per kilometre as `m:ss` in the active unit (per km or per mile); out-of-range renders `--:--`. */
fun formatPace(secPerKm: Double): String {
    if (secPerKm.isNaN() || secPerKm <= 0.0 || secPerKm > 3600.0) return "--:--"
    val perUnit = if (Units.imperial) secPerKm * KM_PER_MILE else secPerKm
    val s = perUnit.toLong()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Speed in m/s as `w.d` in the active unit (km/h or mph). */
fun formatSpeed(mps: Double): String {
    val value = mps * (if (Units.imperial) MPS_TO_MPH else 3.6)
    return String.format(java.util.Locale.US, "%.1f", value)
}

/** Elevation in metres as a whole number in the active unit (m or ft). */
fun formatElevation(metres: Double): String {
    val value = if (Units.imperial) metres * METRES_TO_FEET else metres
    return Math.round(value).toString()
}

/** Distance unit label for the active system. */
fun distanceUnit(t: Texts): String = if (Units.imperial) t.miles else t.km

/** Speed unit label for the active system. */
fun speedUnit(t: Texts): String = if (Units.imperial) t.mph else t.kmh

/** Elevation unit label for the active system. */
fun elevationUnit(t: Texts): String = if (Units.imperial) t.feet else t.metre

private val TODAY_WORDS: Map<Lang, String> = mapOf(
    Lang.AR to "اليوم", Lang.FR to "Aujourd'hui", Lang.EN to "Today",
    Lang.ES to "Hoy", Lang.PT to "Hoje", Lang.DE to "Heute", Lang.IT to "Oggi",
    Lang.NL to "Vandaag", Lang.PL to "Dzisiaj", Lang.RU to "Сегодня", Lang.TR to "Bugün",
    Lang.ID to "Hari ini", Lang.HI to "आज", Lang.JA to "今日", Lang.KO to "오늘", Lang.ZH to "今天",
)

private val YESTERDAY_WORDS: Map<Lang, String> = mapOf(
    Lang.AR to "أمس", Lang.FR to "Hier", Lang.EN to "Yesterday",
    Lang.ES to "Ayer", Lang.PT to "Ontem", Lang.DE to "Gestern", Lang.IT to "Ieri",
    Lang.NL to "Gisteren", Lang.PL to "Wczoraj", Lang.RU to "Вчера", Lang.TR to "Dün",
    Lang.ID to "Kemarin", Lang.HI to "कल", Lang.JA to "昨日", Lang.KO to "어제", Lang.ZH to "昨天",
)

/** A start stamp as `15 أكتوبر 2026 · 21:33`, or today / yesterday for the two most recent calendar days. */
fun formatDate(millis: Long, lang: Lang = Lang.AR, now: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = c.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val dayGap = n.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) +
        if (sameYear) 0 else 365 * (n.get(Calendar.YEAR) - c.get(Calendar.YEAR))
    val whenWord = when (dayGap) {
        0 -> TODAY_WORDS[lang] ?: "اليوم"
        1 -> YESTERDAY_WORDS[lang] ?: "أمس"
        else -> java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.forLanguageTag(lang.code))
            .format(java.util.Date(millis))
    }
    val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$whenWord · $hh:$mm"
}
