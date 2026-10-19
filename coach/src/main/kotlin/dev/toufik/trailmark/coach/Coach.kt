package dev.toufik.trailmark.coach

enum class Locale { AR, FR, EN }

enum class TurnDirection {
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
}

/** The moments the coach speaks at. Text only — never audio. */
sealed interface CoachEvent {
    /** Before a run, comparing the goal with the user's own best on this distance. */
    data class PreviousBest(val distanceM: Double, val seconds: Long, val targetM: Double?) : CoachEvent
    data class Ahead(val seconds: Long) : CoachEvent
    data class Behind(val seconds: Long) : CoachEvent
    data class GhostAhead(val metres: Int) : CoachEvent
    data class GhostBehind(val metres: Int) : CoachEvent
    data class Overtook(val metres: Int) : CoachEvent
    data class GotOvertaken(val metres: Int) : CoachEvent
    data class Milestone(val km: Int) : CoachEvent
    data class SegmentStart(val name: String) : CoachEvent
    data class SegmentFinish(val name: String, val seconds: Long) : CoachEvent
    data class Turn(val direction: TurnDirection, val metres: Int) : CoachEvent
    data object OffRoute : CoachEvent
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
            is CoachEvent.GhostAhead -> fill(pick, "m" to event.metres.toString())
            is CoachEvent.GhostBehind -> fill(pick, "m" to event.metres.toString())
            is CoachEvent.Overtook -> fill(pick, "m" to event.metres.toString())
            is CoachEvent.GotOvertaken -> fill(pick, "m" to event.metres.toString())
            is CoachEvent.Milestone -> fill(pick, "km" to event.km.toString())
            is CoachEvent.SegmentStart -> fill(pick, "name" to event.name)
            is CoachEvent.SegmentFinish -> fill(
                pick,
                "name" to event.name,
                "time" to clock(event.seconds),
            )
            is CoachEvent.Turn -> fill(pick, "m" to event.metres.toString())
            is CoachEvent.OffRoute -> pick
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
        is CoachEvent.GhostAhead -> when (locale) {
            Locale.AR -> listOf("شبحك متقدم بـ {m} م — أدركه!", "خلف شبحك بـ {m} م. زد السرعة قليلاً.", "الشبح أمامك بـ {m} م — لا تستسلم.")
            Locale.FR -> listOf("Ton fantôme mène de {m} m — rattrape-le !", "{m} m de retard sur ton fantôme. Accélère.", "Le fantôme est devant de {m} m — lâche rien.")
            Locale.EN -> listOf("Your ghost leads by {m}m — reel it in!", "{m}m behind your ghost — pick it up.", "Ghost is {m}m ahead — don't fold.")
        }
        is CoachEvent.GhostBehind -> when (locale) {
            Locale.AR -> listOf("أنت متقدم على شبحك بـ {m} م — واصل!", "تقدمك {m} م عن شبحك. لا تفرّط بها.", "الشبح خلفك بـ {m} م — حافظ على الإيقاع.")
            Locale.FR -> listOf("Tu mènes de {m} m sur ton fantôme — tiens bon !", "{m} m d'avance. Ne lâche pas.", "Fantôme derrière de {m} m — garde le rythme.")
            Locale.EN -> listOf("You're {m}m ahead of your ghost — hold it!", "{m}m up. Don't give it back.", "Ghost is {m}m back — keep the pace.")
        }
        is CoachEvent.Overtook -> when (locale) {
            Locale.AR -> listOf("تجاوزت شبحك! 🔥", "سبقته — أنت متقدم بـ {m} م الآن.", "التجاوز تم! {m} م لصالحك.")
            Locale.FR -> listOf("Tu as dépassé ton fantôme ! 🔥", "Dépassement — {m} m d'avance.", "Tu l'as eu ! {m} m devant.")
            Locale.EN -> listOf("You overtook your ghost! 🔥", "Passed it — {m}m ahead now.", "Got him! {m}m in front.")
        }
        is CoachEvent.GotOvertaken -> when (locale) {
            Locale.AR -> listOf("سبقك شبحك — ردّ الهجوم!", "الشبح تقدم — فارق {m} م. استعد.", "تجاوَزك شبحك بـ {m} م — اشتعل!")
            Locale.FR -> listOf("Ton fantôme t'a dépassé — réagis !", "Le fantôme passe devant — {m} m.", "Dépassé de {m} m — réveille-toi !")
            Locale.EN -> listOf("Your ghost overtook you — fight back!", "Ghost took the lead — {m}m gap.", "Overtaken by {m}m — light it up!")
        }
        is CoachEvent.Milestone -> when (locale) {
            Locale.AR -> listOf("{km} كم! استمر", "تجاوزت {km} كم — أحسنت.")
            Locale.FR -> listOf("{km} km ! Continue", "{km} km passés — bien joué.")
            Locale.EN -> listOf("{km} km down! Keep going", "{km} km in — nice work.")
        }
        is CoachEvent.SegmentStart -> when (locale) {
            Locale.AR -> listOf("بداية المقطع: {name} — ركّز!", "مقطع {name} يبدأ الآن.")
            Locale.FR -> listOf("Début du segment : {name} — concentre-toi !", "Segment {name} — c'est parti.")
            Locale.EN -> listOf("Segment start: {name} — focus!", "Segment {name} begins now.")
        }
        is CoachEvent.SegmentFinish -> when (locale) {
            Locale.AR -> listOf("أنهيت مقطع {name} في {time} — أحسنت!", "نهاية {name}: {time}. عمل رائع.")
            Locale.FR -> listOf("Segment {name} terminé en {time} !", "Fin du segment {name} — {time}.")
            Locale.EN -> listOf("Segment {name} done in {time}!", "Finished {name} — {time}.")
        }
        is CoachEvent.Turn -> when (event.direction) {
            TurnDirection.LEFT -> when (locale) {
                Locale.AR -> listOf("انعطف يساراً بعد {m} متر.", "يساراً بعد {m} متر.")
                Locale.FR -> listOf("Tournez à gauche dans {m} m.", "À gauche dans {m} m.")
                Locale.EN -> listOf("Turn left in {m} m.", "Left in {m} m.")
            }
            TurnDirection.RIGHT -> when (locale) {
                Locale.AR -> listOf("انعطف يميناً بعد {m} متر.", "يميناً بعد {m} متر.")
                Locale.FR -> listOf("Tournez à droite dans {m} m.", "À droite dans {m} m.")
                Locale.EN -> listOf("Turn right in {m} m.", "Right in {m} m.")
            }
            TurnDirection.SLIGHT_LEFT -> when (locale) {
                Locale.AR -> listOf("انحرف قليلاً إلى اليسار بعد {m} متر.", "ميل يسير لليسار بعد {m} متر.")
                Locale.FR -> listOf("Serrez à gauche dans {m} m.", "Légèrement à gauche dans {m} m.")
                Locale.EN -> listOf("Bear left in {m} m.", "Keep left in {m} m.")
            }
            TurnDirection.SLIGHT_RIGHT -> when (locale) {
                Locale.AR -> listOf("انحرف قليلاً إلى اليمين بعد {m} متر.", "ميل يسير لليمين بعد {m} متر.")
                Locale.FR -> listOf("Serrez à droite dans {m} m.", "Légèrement à droite dans {m} m.")
                Locale.EN -> listOf("Bear right in {m} m.", "Keep right in {m} m.")
            }
            TurnDirection.SHARP_LEFT -> when (locale) {
                Locale.AR -> listOf("منعطف حاد إلى اليسار بعد {m} متر.", "يسار حاد بعد {m} متر.")
                Locale.FR -> listOf("Virage serré à gauche dans {m} m.", "Gauche serré dans {m} m.")
                Locale.EN -> listOf("Sharp left in {m} m.", "Hard left in {m} m.")
            }
            TurnDirection.SHARP_RIGHT -> when (locale) {
                Locale.AR -> listOf("منعطف حاد إلى اليمين بعد {m} متر.", "يمين حاد بعد {m} متر.")
                Locale.FR -> listOf("Virage serré à droite dans {m} m.", "Droite serré dans {m} m.")
                Locale.EN -> listOf("Sharp right in {m} m.", "Hard right in {m} m.")
            }
            TurnDirection.UTURN -> when (locale) {
                Locale.AR -> listOf("استدر للخلف بعد {m} متر.", "دوران كامل بعد {m} متر.")
                Locale.FR -> listOf("Faites demi-tour dans {m} m.", "Demi-tour dans {m} m.")
                Locale.EN -> listOf("Make a U-turn in {m} m.", "U-turn in {m} m.")
            }
        }
        is CoachEvent.OffRoute -> when (locale) {
            Locale.AR -> listOf(
                "أنت خارج المسار — ارجع إلى الخط المخطط.",
                "خارج المسار. عد إلى المسار المخطط.",
            )
            Locale.FR -> listOf(
                "Vous avez quitté l'itinéraire — revenez sur le tracé.",
                "Hors itinéraire. Rejoignez le tracé.",
            )
            Locale.EN -> listOf(
                "You're off route — head back to the planned line.",
                "Off route. Return to the planned line.",
            )
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
