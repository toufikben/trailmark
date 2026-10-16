package dev.toufik.trailmark.coach

enum class Locale { AR, FR, EN }

/** The moments the coach speaks at. Text only — never audio. */
sealed interface CoachEvent {
    /** Before a run, comparing the goal with the user's own best on this distance. */
    data class PreviousBest(val distanceM: Double, val seconds: Long, val targetM: Double?) : CoachEvent
    data class Ahead(val seconds: Long) : CoachEvent
    data class Behind(val seconds: Long) : CoachEvent
    data class Milestone(val km: Int) : CoachEvent
    data class Finish(val distanceM: Double, val seconds: Long, val isRecord: Boolean) : CoachEvent
}

private fun clock(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun km(distanceM: Double): String {
    val whole = (distanceM / 1000.0)
    return if (whole % 1.0 == 0.0) whole.toInt().toString() else "%.2f".format(whole)
}

private fun fill(template: String, vararg pairs: Pair<String, String>): String {
    var out = template
    for ((k, v) in pairs) out = out.replace("{$k}", v)
    return out
}

/**
 * Local rule-based coach. A bank of hand-written templates per locale, chosen by
 * a seeded index so the same moment does not repeat the same sentence.
 */
object Coach {

    fun message(event: CoachEvent, locale: Locale, seed: Int = 0): String {
        val variants = variantsFor(event, locale)
        val pick = variants[Math.floorMod(seed, variants.size)]
        return when (event) {
            is CoachEvent.PreviousBest -> fill(
                pick,
                "km" to km(event.targetM ?: event.distanceM),
                "time" to clock(event.seconds),
            )
            is CoachEvent.Ahead -> fill(pick, "s" to event.seconds.toString())
            is CoachEvent.Behind -> fill(pick, "s" to event.seconds.toString())
            is CoachEvent.Milestone -> fill(pick, "km" to event.km.toString())
            is CoachEvent.Finish -> fill(
                pick,
                "km" to km(event.distanceM),
                "time" to clock(event.seconds),
            )
        }
    }

    private fun variantsFor(event: CoachEvent, locale: Locale): List<String> = when (event) {
        is CoachEvent.PreviousBest -> when (locale) {
            Locale.AR -> listOf(
                "آخر مرة {km} كم في {time} — تكسرها اليوم؟ لا تكون كسول 😉",
                "{km} كم في {time} في الجيب. أظهر أنك أقوى اليوم.",
            )
            Locale.FR -> listOf(
                "La dernière fois : {km} km en {time}. Tu fais mieux aujourd'hui ? Sois pas fainéant 😉",
                "{km} km en {time} dans la poche. Montre qui est le patron aujourd'hui.",
            )
            Locale.EN -> listOf(
                "Last time: {km} km in {time}. Beating it today? Don't be lazy 😉",
                "{km} km in {time} in the bag. Show up stronger today.",
            )
        }
        is CoachEvent.Ahead -> when (locale) {
            Locale.AR -> listOf("متقدّم بـ {s} ثانية على نسختك السابقة — حافظ على الوتيرة!", "أنت أمام شبحك بـ {s} ث. لا تتراجع الآن.")
            Locale.FR -> listOf("Tu devances ton fantôme de {s} s — tiens le rythme !", "{s} s d'avance. Ne lâche rien.")
            Locale.EN -> listOf("You're {s}s ahead of your ghost — hold the pace!", "{s}s up. Don't give it back now.")
        }
        is CoachEvent.Behind -> when (locale) {
            Locale.AR -> listOf("متأخّر بـ {s} ثانية — اشتدّ قليلاً!", "خلف شبحك بـ {s} ث. أعطِ أكثر قليلاً.")
            Locale.FR -> listOf("Retard de {s} s — accélère un peu !", "{s} s de retard. Donne un peu plus.")
            Locale.EN -> listOf("{s}s behind — push a little!", "{s}s down. Give a bit more.")
        }
        is CoachEvent.Milestone -> when (locale) {
            Locale.AR -> listOf("{km} كم! استمر", "تجاوزت {km} كم — أحسنت.")
            Locale.FR -> listOf("{km} km ! Continue", "{km} km passés — bien joué.")
            Locale.EN -> listOf("{km} km down! Keep going", "{km} km in — nice work.")
        }
        is CoachEvent.Finish -> when (locale) {
            Locale.AR -> listOf(
                "أنهيت {km} كم في {time}. رقم جديد؟ 🎉",
                "{km} كم في {time} — إنجاز يستحق.",
            )
            Locale.FR -> listOf("{km} km en {time}. Record ? 🎉", "{km} km en {time} — belle sortie.")
            Locale.EN -> listOf("{km} km in {time}. New record? 🎉", "{km} km in {time} — strong finish.")
        }
    }
}
