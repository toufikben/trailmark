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
    data class SegmentRecord(val name: String, val seconds: Long, val improvedBySeconds: Long) : CoachEvent
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
    return if (whole % 1.0 == 0.0) whole.toInt().toString() else "%.2f".format(java.util.Locale.US, whole)
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
            is CoachEvent.SegmentRecord -> fill(
                pick,
                "name" to event.name,
                "time" to clock(event.seconds),
                "gain" to clock(event.improvedBySeconds),
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
                "آخر جولة: {km} كم في {time}. اليوم ابدأ بثبات وابنِ الإيقاع.",
                "{km} كم في {time} هو رقمك السابق. خطوة خطوة وستتجاوزه.",
            )
            Locale.FR -> listOf(
                "Dernière sortie : {km} km en {time}. Aujourd'hui, pars régulier et construis ton rythme.",
                "{km} km en {time}, c'est ton repère. Pas à pas, tu vas le battre.",
            )
            Locale.EN -> listOf(
                "Last time: {km} km in {time}. Today, start steady and build the pace.",
                "{km} km in {time} is your mark. Step by step, you'll beat it.",
            )
        }
        is CoachEvent.Ahead -> when (locale) {
            Locale.AR -> listOf("متقدم بـ {s} ث على شبحك — حافظ على هذا الإيقاع.", "أنت أمام الشبح بـ {s} ث. ثبّت النفس ولا تغيّر خطتك.")
            Locale.FR -> listOf("{s} s d'avance sur ton fantôme — garde ce rythme.", "Tu mènes de {s} s. Reste régulier.")
            Locale.EN -> listOf("{s}s ahead of your ghost — hold this pace.", "{s}s up. Stay steady.")
        }
        is CoachEvent.Behind -> when (locale) {
            Locale.AR -> listOf("بينك وبين الشبح {s} ث. ارفع الإيقاع تدريجيًا.", "متأخر {s} ث — خطوة أسرع في الدقيقة القادمة تكفي.")
            Locale.FR -> listOf("{s} s de retard. Monte le rythme progressivement.", "{s} s à reprendre — une foulée plus vive suffit.")
            Locale.EN -> listOf("{s}s to close. Lift the pace gradually.", "{s}s behind — one stronger stride at a time.")
        }
        is CoachEvent.GhostAhead -> when (locale) {
            Locale.AR -> listOf("الشبح متقدم بـ {m} م. قرّب المسافة بهدوء.", "الفارق {m} م. ركّز على الخطوة التالية.", "الشبح أمامك بـ {m} م — كل متر يقلّص الفارق.")
            Locale.FR -> listOf("Ton fantôme mène de {m} m. Reviens mètre par mètre.", "Écart : {m} m. Concentre-toi sur la prochaine foulée.", "Le fantôme est {m} m devant — chaque mètre compte.")
            Locale.EN -> listOf("Your ghost leads by {m}m. Close the gap metre by metre.", "{m}m gap. Focus on the next stride.", "Ghost is {m}m ahead — every metre counts.")
        }
        is CoachEvent.GhostBehind -> when (locale) {
            Locale.AR -> listOf("أنت متقدم بـ {m} م. حافظ على الإيقاع.", "تقدمك {m} م — ثبّت السرعة.", "الشبح خلفك بـ {m} م. استمر بنفس الوتيرة.")
            Locale.FR -> listOf("Tu mènes de {m} m. Garde ce rythme.", "{m} m d'avance — reste régulier.", "Fantôme {m} m derrière. Continue comme ça.")
            Locale.EN -> listOf("You're {m}m ahead. Hold this pace.", "{m}m up — stay steady.", "Ghost is {m}m behind. Keep it going.")
        }
        is CoachEvent.Overtook -> when (locale) {
            Locale.AR -> listOf("تجاوزت الشبح! الفارق الآن {m} م.", "أنت في المقدمة بـ {m} م — واصل.", "تجاوز ناجح. حافظ على تفوق {m} م.")
            Locale.FR -> listOf("Tu as dépassé ton fantôme ! {m} m d'avance.", "Tu passes devant — {m} m.", "Dépassement réussi. Garde {m} m d'avance.")
            Locale.EN -> listOf("You passed your ghost! {m}m ahead.", "You're in front — {m}m.", "Clean overtake. Keep {m}m of daylight.")
        }
        is CoachEvent.GotOvertaken -> when (locale) {
            Locale.AR -> listOf("الشبح تقدم. الفارق {m} م — ارفع إيقاعك قليلاً.", "تأخرت {m} م. عد إلى وتيرتك القوية.", "الشبح أمامك الآن بـ {m} م. سباق طويل، واصل.")
            Locale.FR -> listOf("Le fantôme est passé. {m} m à reprendre, sans forcer.", "Écart : {m} m. Reprends ton rythme fort.", "Le fantôme mène de {m} m. La course est encore longue.")
            Locale.EN -> listOf("Your ghost moved ahead. {m}m to close — lift smoothly.", "{m}m gap. Get back to your strong rhythm.", "Ghost leads by {m}m. Long race — keep going.")
        }
        is CoachEvent.Milestone -> when (locale) {
            Locale.AR -> listOf("{km} كم — علامة جديدة في هذه الجولة.", "{km} كم. حافظ على هذه الوتيرة.")
            Locale.FR -> listOf("{km} km — un nouveau palier sur cette sortie.", "{km} km parcourus. Tiens ce rythme.")
            Locale.EN -> listOf("{km} km — a new marker this run.", "{km} km done. Hold this rhythm.")
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
        is CoachEvent.SegmentRecord -> when (locale) {
            Locale.AR -> listOf(
                "رقم قياسي جديد على {name}: {time}، أسرع بـ {gain}!",
                "حطّمت رقمك على {name} — {time}، تحسّن {gain}.",
            )
            Locale.FR -> listOf(
                "Nouveau record sur {name} : {time}, {gain} de mieux !",
                "Record battu sur {name} — {time}, {gain} gagnées.",
            )
            Locale.EN -> listOf(
                "New record on {name}: {time}, {gain} faster!",
                "You beat your best on {name} — {time}, {gain} quicker.",
            )
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
                "أنهيت {km} كم في {time}. جولة تستحق الاحتفاء.",
                "{km} كم في {time} — عمل متين. استعد للجولة القادمة.",
            )
            Locale.FR -> listOf(
                "Terminé : {km} km en {time}. Belle sortie.",
                "{km} km en {time} — du travail solide.",
            )
            Locale.EN -> listOf(
                "Done: {km} km in {time}. A run worth celebrating.",
                "{km} km in {time} — solid work. Recover and come back stronger.",
            )
        }
    }
}
