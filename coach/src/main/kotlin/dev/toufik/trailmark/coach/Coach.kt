package dev.toufik.trailmark.coach

enum class Locale(val tag: String) {
    AR("ar"),
    EN("en"),
    FR("fr"),
    ES("es"),
    PT("pt"),
    DE("de"),
    IT("it"),
    NL("nl"),
    PL("pl"),
    RU("ru"),
    TR("tr"),
    ID("id"),
    HI("hi"),
    JA("ja"),
    KO("ko"),
    ZH("zh"),
}

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
 *
 * Every UI language has its own bank; a missing key falls back to English
 * instead of reading the key out loud.
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

    private fun variantsFor(event: CoachEvent, locale: Locale): List<String> {
        val key = when (event) {
            is CoachEvent.PreviousBest -> "previousBest"
            is CoachEvent.Ahead -> "ahead"
            is CoachEvent.Behind -> "behind"
            is CoachEvent.GhostAhead -> "ghostAhead"
            is CoachEvent.GhostBehind -> "ghostBehind"
            is CoachEvent.Overtook -> "overtook"
            is CoachEvent.GotOvertaken -> "gotOvertaken"
            is CoachEvent.Milestone -> "milestone"
            is CoachEvent.SegmentStart -> "segmentStart"
            is CoachEvent.SegmentFinish -> "segmentFinish"
            is CoachEvent.SegmentRecord -> "segmentRecord"
            is CoachEvent.Turn -> when (event.direction) {
                TurnDirection.LEFT -> "left"
                TurnDirection.RIGHT -> "right"
                TurnDirection.SLIGHT_LEFT -> "slightLeft"
                TurnDirection.SLIGHT_RIGHT -> "slightRight"
                TurnDirection.SHARP_LEFT -> "sharpLeft"
                TurnDirection.SHARP_RIGHT -> "sharpRight"
                TurnDirection.UTURN -> "uturn"
            }
            is CoachEvent.OffRoute -> "offRoute"
            is CoachEvent.Finish -> "finish"
        }
        return COACH_BANKS[locale]?.get(key)
            ?: COACH_BANKS.getValue(Locale.EN).getValue(key)
    }
}

internal val COACH_BANKS: Map<Locale, Map<String, List<String>>> = mapOf(
    Locale.AR to mapOf(
        "previousBest" to listOf(
            "آخر جولة: {km} كم في {time}. اليوم ابدأ بثبات وابنِ الإيقاع.",
            "{km} كم في {time} هو رقمك السابق. خطوة خطوة وستتجاوزه.",
        ),
        "ahead" to listOf(
            "متقدم بـ {s} ث على شبحك — حافظ على هذا الإيقاع.",
            "أنت أمام الشبح بـ {s} ث. ثبّت النفس ولا تغيّر خطتك.",
        ),
        "behind" to listOf(
            "بينك وبين الشبح {s} ث. ارفع الإيقاع تدريجيًا.",
            "متأخر {s} ث — خطوة أسرع في الدقيقة القادمة تكفي.",
        ),
        "ghostAhead" to listOf(
            "الشبح متقدم بـ {m} م. قرّب المسافة بهدوء.",
            "الفارق {m} م. ركّز على الخطوة التالية.",
            "الشبح أمامك بـ {m} م — كل متر يقلّص الفارق.",
        ),
        "ghostBehind" to listOf(
            "أنت متقدم بـ {m} م. حافظ على الإيقاع.",
            "تقدمك {m} م — ثبّت السرعة.",
            "الشبح خلفك بـ {m} م. استمر بنفس الوتيرة.",
        ),
        "overtook" to listOf(
            "تجاوزت الشبح! الفارق الآن {m} م.",
            "أنت في المقدمة بـ {m} م — واصل.",
            "تجاوز ناجح. حافظ على تفوق {m} م.",
        ),
        "gotOvertaken" to listOf(
            "الشبح تقدم. الفارق {m} م — ارفع إيقاعك قليلاً.",
            "تأخرت {m} م. عد إلى وتيرتك القوية.",
            "الشبح أمامك الآن بـ {m} م. سباق طويل، واصل.",
        ),
        "milestone" to listOf(
            "{km} كم — علامة جديدة في هذه الجولة.",
            "{km} كم. حافظ على هذه الوتيرة.",
        ),
        "segmentStart" to listOf("بداية المقطع: {name} — ركّز!", "مقطع {name} يبدأ الآن."),
        "segmentFinish" to listOf("أنهيت مقطع {name} في {time} — أحسنت!", "نهاية {name}: {time}. عمل رائع."),
        "segmentRecord" to listOf(
            "رقم قياسي جديد على {name}: {time}، أسرع بـ {gain}!",
            "حطّمت رقمك على {name} — {time}، تحسّن {gain}.",
        ),
        "left" to listOf("انعطف يساراً بعد {m} متر.", "يساراً بعد {m} متر."),
        "right" to listOf("انعطف يميناً بعد {m} متر.", "يميناً بعد {m} متر."),
        "slightLeft" to listOf("انحرف قليلاً إلى اليسار بعد {m} متر.", "ميل يسير لليسار بعد {m} متر."),
        "slightRight" to listOf("انحرف قليلاً إلى اليمين بعد {m} متر.", "ميل يسير لليمين بعد {m} متر."),
        "sharpLeft" to listOf("منعطف حاد إلى اليسار بعد {m} متر.", "يسار حاد بعد {m} متر."),
        "sharpRight" to listOf("منعطف حاد إلى اليمين بعد {m} متر.", "يمين حاد بعد {m} متر."),
        "uturn" to listOf("استدر للخلف بعد {m} متر.", "دوران كامل بعد {m} متر."),
        "offRoute" to listOf(
            "أنت خارج المسار — ارجع إلى الخط المخطط.",
            "خارج المسار. عد إلى المسار المخطط.",
        ),
        "finish" to listOf(
            "أنهيت {km} كم في {time}. جولة تستحق الاحتفاء.",
            "{km} كم في {time} — عمل متين. استعد للجولة القادمة.",
        ),
    ),
    Locale.EN to mapOf(
        "previousBest" to listOf(
            "Last time: {km} km in {time}. Today, start steady and build the pace.",
            "{km} km in {time} is your mark. Step by step, you'll beat it.",
        ),
        "ahead" to listOf(
            "{s}s ahead of your ghost — hold this pace.",
            "{s}s up. Stay steady.",
        ),
        "behind" to listOf(
            "{s}s to close. Lift the pace gradually.",
            "{s}s behind — one stronger stride at a time.",
        ),
        "ghostAhead" to listOf(
            "Your ghost leads by {m}m. Close the gap metre by metre.",
            "{m}m gap. Focus on the next stride.",
            "Ghost is {m}m ahead — every metre counts.",
        ),
        "ghostBehind" to listOf(
            "You're {m}m ahead. Hold this pace.",
            "{m}m up — stay steady.",
            "Ghost is {m}m behind. Keep it going.",
        ),
        "overtook" to listOf(
            "You passed your ghost! {m}m ahead.",
            "You're in front — {m}m.",
            "Clean overtake. Keep {m}m of daylight.",
        ),
        "gotOvertaken" to listOf(
            "Your ghost moved ahead. {m}m to close — lift smoothly.",
            "{m}m gap. Get back to your strong rhythm.",
            "Ghost leads by {m}m. Long race — keep going.",
        ),
        "milestone" to listOf(
            "{km} km — a new marker this run.",
            "{km} km done. Hold this rhythm.",
        ),
        "segmentStart" to listOf("Segment start: {name} — focus!", "Segment {name} begins now."),
        "segmentFinish" to listOf("Segment {name} done in {time}!", "Finished {name} — {time}."),
        "segmentRecord" to listOf(
            "New record on {name}: {time}, {gain} faster!",
            "You beat your best on {name} — {time}, {gain} quicker.",
        ),
        "left" to listOf("Turn left in {m} m.", "Left in {m} m."),
        "right" to listOf("Turn right in {m} m.", "Right in {m} m."),
        "slightLeft" to listOf("Bear left in {m} m.", "Keep left in {m} m."),
        "slightRight" to listOf("Bear right in {m} m.", "Keep right in {m} m."),
        "sharpLeft" to listOf("Sharp left in {m} m.", "Hard left in {m} m."),
        "sharpRight" to listOf("Sharp right in {m} m.", "Hard right in {m} m."),
        "uturn" to listOf("Make a U-turn in {m} m.", "U-turn in {m} m."),
        "offRoute" to listOf(
            "You're off route — head back to the planned line.",
            "Off route. Return to the planned line.",
        ),
        "finish" to listOf(
            "Done: {km} km in {time}. A run worth celebrating.",
            "{km} km in {time} — solid work. Recover and come back stronger.",
        ),
    ),
    Locale.FR to mapOf(
        "previousBest" to listOf(
            "Dernière sortie : {km} km en {time}. Aujourd'hui, pars régulier et construis ton rythme.",
            "{km} km en {time}, c'est ton repère. Pas à pas, tu vas le battre.",
        ),
        "ahead" to listOf(
            "{s} s d'avance sur ton fantôme — garde ce rythme.",
            "Tu mènes de {s} s. Reste régulier.",
        ),
        "behind" to listOf(
            "{s} s de retard. Monte le rythme progressivement.",
            "{s} s à reprendre — une foulée plus vive suffit.",
        ),
        "ghostAhead" to listOf(
            "Ton fantôme mène de {m} m. Reviens mètre par mètre.",
            "Écart : {m} m. Concentre-toi sur la prochaine foulée.",
            "Le fantôme est {m} m devant — chaque mètre compte.",
        ),
        "ghostBehind" to listOf(
            "Tu mènes de {m} m. Garde ce rythme.",
            "{m} m d'avance — reste régulier.",
            "Fantôme {m} m derrière. Continue comme ça.",
        ),
        "overtook" to listOf(
            "Tu as dépassé ton fantôme ! {m} m d'avance.",
            "Tu passes devant — {m} m.",
            "Dépassement réussi. Garde {m} m d'avance.",
        ),
        "gotOvertaken" to listOf(
            "Le fantôme est passé. {m} m à reprendre, sans forcer.",
            "Écart : {m} m. Reprends ton rythme fort.",
            "Le fantôme mène de {m} m. La course est encore longue.",
        ),
        "milestone" to listOf(
            "{km} km — un nouveau palier sur cette sortie.",
            "{km} km parcourus. Tiens ce rythme.",
        ),
        "segmentStart" to listOf("Début du segment : {name} — concentre-toi !", "Segment {name} — c'est parti."),
        "segmentFinish" to listOf("Segment {name} terminé en {time} !", "Fin du segment {name} — {time}."),
        "segmentRecord" to listOf(
            "Nouveau record sur {name} : {time}, {gain} de mieux !",
            "Record battu sur {name} — {time}, {gain} gagnées.",
        ),
        "left" to listOf("Tournez à gauche dans {m} m.", "À gauche dans {m} m."),
        "right" to listOf("Tournez à droite dans {m} m.", "À droite dans {m} m."),
        "slightLeft" to listOf("Serrez à gauche dans {m} m.", "Légèrement à gauche dans {m} m."),
        "slightRight" to listOf("Serrez à droite dans {m} m.", "Légèrement à droite dans {m} m."),
        "sharpLeft" to listOf("Virage serré à gauche dans {m} m.", "Gauche serré dans {m} m."),
        "sharpRight" to listOf("Virage serré à droite dans {m} m.", "Droite serrée dans {m} m."),
        "uturn" to listOf("Faites demi-tour dans {m} m.", "Demi-tour dans {m} m."),
        "offRoute" to listOf(
            "Vous avez quitté l'itinéraire — revenez sur le tracé.",
            "Hors itinéraire. Rejoignez le tracé.",
        ),
        "finish" to listOf(
            "Terminé : {km} km en {time}. Belle sortie.",
            "{km} km en {time} — du travail solide.",
        ),
    ),
    Locale.ES to mapOf(
        "previousBest" to listOf(
            "Última salida: {km} km en {time}. Hoy empieza constante y sube el ritmo.",
            "{km} km en {time} es tu marca. Paso a paso la vas a batir.",
        ),
        "ahead" to listOf(
            "{s} s de ventaja sobre tu fantasma — mantén este ritmo.",
            "Vas {s} s por delante. Mantén la calma.",
        ),
        "behind" to listOf(
            "{s} s por recuperar. Sube el ritmo poco a poco.",
            "Estás {s} s por detrás — una zancada más fuerte basta.",
        ),
        "ghostAhead" to listOf(
            "Tu fantasma va {m} m por delante. Recorta metro a metro.",
            "Faltan {m} m para alcanzarlo.",
        ),
        "ghostBehind" to listOf(
            "Vas {m} m por delante. Mantén el ritmo.",
            "{m} m de ventaja — sigue así.",
        ),
        "overtook" to listOf(
            "¡Has pasado a tu fantasma! {m} m de ventaja.",
            "Adelantaste — {m} m por delante.",
        ),
        "gotOvertaken" to listOf(
            "Tu fantasma se ha ido. {m} m por recuperar.",
            "El fantasma va {m} m delante. Queda carrera.",
        ),
        "milestone" to listOf(
            "{km} km — nueva marca en esta salida.",
            "{km} km hechos. Mantén el ritmo.",
        ),
        "segmentStart" to listOf("Empieza el segmento {name} — ¡concéntrate!"),
        "segmentFinish" to listOf("Segmento {name} terminado en {time}."),
        "segmentRecord" to listOf("¡Nuevo récord en {name}: {time}, {gain} más rápido!"),
        "left" to listOf("Gira a la izquierda en {m} m."),
        "right" to listOf("Gira a la derecha en {m} m."),
        "slightLeft" to listOf("Inclínate a la izquierda en {m} m."),
        "slightRight" to listOf("Inclínate a la derecha en {m} m."),
        "sharpLeft" to listOf("Curva cerrada a la izquierda en {m} m."),
        "sharpRight" to listOf("Curva cerrada a la derecha en {m} m."),
        "uturn" to listOf("Da la vuelta en {m} m."),
        "offRoute" to listOf("Fuera de ruta — vuelve a la línea marcada."),
        "finish" to listOf(
            "Terminado: {km} km en {time}. Buena salida.",
            "{km} km en {time} — trabajo sólido.",
        ),
    ),
    Locale.PT to mapOf(
        "previousBest" to listOf(
            "Última saída: {km} km em {time}. Hoje começa constante e constrói o ritmo.",
            "{km} km em {time} é a tua marca. Passo a passo vais batê-la.",
        ),
        "ahead" to listOf(
            "{s} s de vantagem sobre o teu fantasma — mantém este ritmo.",
            "Vais {s} s à frente. Mantém a calma.",
        ),
        "behind" to listOf(
            "{s} s para recuperar. Sobe o ritmo aos poucos.",
            "Estás {s} s atrás — uma passada mais forte basta.",
        ),
        "ghostAhead" to listOf(
            "O teu fantasma vai {m} m à frente. Encurta metro a metro.",
            "Faltam {m} m para o alcançar.",
        ),
        "ghostBehind" to listOf(
            "Vais {m} m à frente. Mantém o ritmo.",
            "{m} m de vantagem — continua.",
        ),
        "overtook" to listOf(
            "Passaste o teu fantasma! {m} m à frente.",
            "Ultrapassagem limpa — {m} m de vantagem.",
        ),
        "gotOvertaken" to listOf(
            "O fantasma passou. {m} m para recuperar.",
            "O fantasma lidera por {m} m. Ainda há corrida.",
        ),
        "milestone" to listOf(
            "{km} km — nova marca nesta saída.",
            "{km} km feitos. Mantém este ritmo.",
        ),
        "segmentStart" to listOf("Começa o segmento {name} — concentra-te!"),
        "segmentFinish" to listOf("Segmento {name} terminado em {time}."),
        "segmentRecord" to listOf("Novo recorde em {name}: {time}, {gain} mais rápido!"),
        "left" to listOf("Vira à esquerda em {m} m."),
        "right" to listOf("Vira à direita em {m} m."),
        "slightLeft" to listOf("Inclina-te à esquerda em {m} m."),
        "slightRight" to listOf("Inclina-te à direita em {m} m."),
        "sharpLeft" to listOf("Curva fechada à esquerda em {m} m."),
        "sharpRight" to listOf("Curva fechada à direita em {m} m."),
        "uturn" to listOf("Dá a volta em {m} m."),
        "offRoute" to listOf("Fora da rota — volta à linha planeada."),
        "finish" to listOf(
            "Terminado: {km} km em {time}. Boa saída.",
            "{km} km em {time} — trabalho sólido.",
        ),
    ),
    Locale.DE to mapOf(
        "previousBest" to listOf(
            "Letztes Mal: {km} km in {time}. Heute ruhig starten und das Tempo aufbauen.",
            "{km} km in {time} ist deine Marke. Schritt für Schritt schlägst du sie.",
        ),
        "ahead" to listOf(
            "{s} s vor deinem Geist — halte das Tempo.",
            "Du liegst {s} s vorn. Bleib ruhig.",
        ),
        "behind" to listOf(
            "{s} s Rückstand. Zieh das Tempo langsam an.",
            "{s} s aufzuholen — ein kräftigerer Schritt reicht.",
        ),
        "ghostAhead" to listOf(
            "Dein Geist ist {m} m voraus. Meter für Meter heran.",
            "{m} m Rückstand. Konzentrier dich auf den nächsten Schritt.",
        ),
        "ghostBehind" to listOf(
            "Du bist {m} m voraus. Halte das Tempo.",
            "{m} m Vorsprung — weiter so.",
        ),
        "overtook" to listOf(
            "Geist überholt! {m} m Vorsprung.",
            "Überholt — {m} m vorn.",
        ),
        "gotOvertaken" to listOf(
            "Der Geist ist vorbei. {m} m aufzuholen.",
            "Geist führt um {m} m. Das Rennen ist noch lang.",
        ),
        "milestone" to listOf(
            "{km} km — neue Marke in dieser Runde.",
            "{km} km geschafft. Halte den Rhythmus.",
        ),
        "segmentStart" to listOf("Segment {name} beginnt — konzentrier dich!"),
        "segmentFinish" to listOf("Segment {name} in {time} beendet."),
        "segmentRecord" to listOf("Neuer Rekord auf {name}: {time}, {gain} schneller!"),
        "left" to listOf("In {m} m links abbiegen."),
        "right" to listOf("In {m} m rechts abbiegen."),
        "slightLeft" to listOf("In {m} m leicht links halten."),
        "slightRight" to listOf("In {m} m leicht rechts halten."),
        "sharpLeft" to listOf("In {m} m scharf links."),
        "sharpRight" to listOf("In {m} m scharf rechts."),
        "uturn" to listOf("In {m} m wenden."),
        "offRoute" to listOf("Du bist vom Weg ab — zurück zur geplanten Linie."),
        "finish" to listOf(
            "Fertig: {km} km in {time}. Starke Runde.",
            "{km} km in {time} — solide Arbeit.",
        ),
    ),
    Locale.IT to mapOf(
        "previousBest" to listOf(
            "Ultima uscita: {km} km in {time}. Oggi parti regolare e costruisci il ritmo.",
            "{km} km in {time} è il tuo riferimento. Passo dopo passo lo batterai.",
        ),
        "ahead" to listOf(
            "{s} s di vantaggio sul fantasma — mantieni questo ritmo.",
            "Sei {s} s avanti. Resta regolare.",
        ),
        "behind" to listOf(
            "{s} s da recuperare. Alza il ritmo piano.",
            "Sei {s} s dietro — basta una falcata più decisa.",
        ),
        "ghostAhead" to listOf(
            "Il fantasma è {m} m avanti. Recupera metro dopo metro.",
            "Mancano {m} m per raggiungerlo.",
        ),
        "ghostBehind" to listOf(
            "Sei {m} m avanti. Mantieni il ritmo.",
            "{m} m di vantaggio — continua così.",
        ),
        "overtook" to listOf(
            "Hai superato il fantasma! {m} m avanti.",
            "Sorpasso netto — {m} m di vantaggio.",
        ),
        "gotOvertaken" to listOf(
            "Il fantasma è passato. {m} m da recuperare.",
            "Il fantasma guida di {m} m. La corsa è lunga.",
        ),
        "milestone" to listOf(
            "{km} km — nuovo traguardo in questa uscita.",
            "{km} km fatti. Tieni il ritmo.",
        ),
        "segmentStart" to listOf("Inizia il segmento {name} — concentrati!"),
        "segmentFinish" to listOf("Segmento {name} chiuso in {time}."),
        "segmentRecord" to listOf("Nuovo record su {name}: {time}, {gain} più veloce!"),
        "left" to listOf("Gira a sinistra tra {m} m."),
        "right" to listOf("Gira a destra tra {m} m."),
        "slightLeft" to listOf("Tieni la sinistra tra {m} m."),
        "slightRight" to listOf("Tieni la destra tra {m} m."),
        "sharpLeft" to listOf("Curva stretta a sinistra tra {m} m."),
        "sharpRight" to listOf("Curva stretta a destra tra {m} m."),
        "uturn" to listOf("Inverti la marcia tra {m} m."),
        "offRoute" to listOf("Fuori percorso — torna sulla linea tracciata."),
        "finish" to listOf(
            "Finito: {km} km in {time}. Bella uscita.",
            "{km} km in {time} — lavoro solido.",
        ),
    ),
    Locale.NL to mapOf(
        "previousBest" to listOf(
            "Vorige keer: {km} km in {time}. Begin vandaag rustig en bouw het tempo op.",
            "{km} km in {time} is je maatstaf. Stap voor stap versla je hem.",
        ),
        "ahead" to listOf(
            "{s} s voor op je spook — houd dit tempo vast.",
            "Je ligt {s} s voor. Blijf rustig.",
        ),
        "behind" to listOf(
            "{s} s in te halen. Voer het tempo rustig op.",
            "Je staat {s} s achter — één stevige stap extra.",
        ),
        "ghostAhead" to listOf(
            "Je spook is {m} m voor. Kruip meter voor meter dichterbij.",
            "Nog {m} m te gaan.",
        ),
        "ghostBehind" to listOf(
            "Je ligt {m} m voor. Houd het tempo.",
            "{m} m voorsprong — ga zo door.",
        ),
        "overtook" to listOf(
            "Je hebt je spook ingehaald! {m} m voor.",
            "Inhaalactie — {m} m voor.",
        ),
        "gotOvertaken" to listOf(
            "Het spook is je voorbij. {m} m in te halen.",
            "Spook leidt met {m} m. Nog een lange race.",
        ),
        "milestone" to listOf(
            "{km} km — een nieuwe mijlpaal in deze rit.",
            "{km} km gedaan. Houd dit ritme.",
        ),
        "segmentStart" to listOf("Segment {name} begint — focus!"),
        "segmentFinish" to listOf("Segment {name} klaar in {time}."),
        "segmentRecord" to listOf("Nieuw record op {name}: {time}, {gain} sneller!"),
        "left" to listOf("Sla over {m} m linksaf."),
        "right" to listOf("Sla over {m} m rechtsaf."),
        "slightLeft" to listOf("Houd over {m} m links aan."),
        "slightRight" to listOf("Houd over {m} m rechts aan."),
        "sharpLeft" to listOf("Scherpe bocht links over {m} m."),
        "sharpRight" to listOf("Scherpe bocht rechts over {m} m."),
        "uturn" to listOf("Keer om over {m} m."),
        "offRoute" to listOf("Je bent van de route — terug naar de lijn."),
        "finish" to listOf(
            "Klaar: {km} km in {time}. Mooie rit.",
            "{km} km in {time} — solide werk.",
        ),
    ),
    Locale.PL to mapOf(
        "previousBest" to listOf(
            "Ostatnio: {km} km w {time}. Dziś zacznij równo i buduj tempo.",
            "{km} km w {time} to twoja granica. Krok po kroku ją pobijesz.",
        ),
        "ahead" to listOf(
            "{s} s przewagi nad duchem — utrzymaj to tempo.",
            "Jesteś {s} s z przodu. Trzymaj równo.",
        ),
        "behind" to listOf(
            "{s} s do odrobienia. Podnoś tempo spokojnie.",
            "Masz {s} s straty — jeden mocniejszy krok wystarczy.",
        ),
        "ghostAhead" to listOf(
            "Duch jest {m} m z przodu. Skracaj dystans metr po metrze.",
            "Zostało {m} m do dogonienia.",
        ),
        "ghostBehind" to listOf(
            "Jesteś {m} m z przodu. Utrzymaj tempo.",
            "{m} m przewagi — tak trzymaj.",
        ),
        "overtook" to listOf(
            "Wyprzedziłeś ducha! {m} m przewagi.",
            "Czyste wyprzedzenie — {m} m z przodu.",
        ),
        "gotOvertaken" to listOf(
            "Duch cię wyprzedził. {m} m do odrobienia.",
            "Duch prowadzi o {m} m. Bieg jest długi.",
        ),
        "milestone" to listOf(
            "{km} km — nowy punkt w tym biegu.",
            "{km} km zrobione. Trzymaj rytm.",
        ),
        "segmentStart" to listOf("Zaczyna się segment {name} — skup się!"),
        "segmentFinish" to listOf("Segment {name} ukończony w {time}."),
        "segmentRecord" to listOf("Nowy rekord na {name}: {time}, o {gain} szybciej!"),
        "left" to listOf("Skręć w lewo za {m} m."),
        "right" to listOf("Skręć w prawo za {m} m."),
        "slightLeft" to listOf("Trzymaj się lekko lewej za {m} m."),
        "slightRight" to listOf("Trzymaj się lekko prawej za {m} m."),
        "sharpLeft" to listOf("Ostry zakręt w lewo za {m} m."),
        "sharpRight" to listOf("Ostry zakręt w prawo za {m} m."),
        "uturn" to listOf("Zawróć za {m} m."),
        "offRoute" to listOf("Zboczyłeś z trasy — wróć na wyznaczoną linię."),
        "finish" to listOf(
            "Koniec: {km} km w {time}. Dobry bieg.",
            "{km} km w {time} — solidna robota.",
        ),
    ),
    Locale.RU to mapOf(
        "previousBest" to listOf(
            "Прошлый раз: {km} км за {time}. Сегодня начни ровно и наращивай темп.",
            "{km} км за {time} — твой ориентир. Шаг за шагом ты его побьёшь.",
        ),
        "ahead" to listOf(
            "Ты на {s} с впереди призрака — держи темп.",
            "Преимущество {s} с. Не сбавляй.",
        ),
        "behind" to listOf(
            "Отставание {s} с. Плавно поднимай темп.",
            "{s} с нужно отыграть — хватит одного сильного шага.",
        ),
        "ghostAhead" to listOf(
            "Призрак впереди на {m} м. Сокращай по метру.",
            "Осталось {m} м до него.",
        ),
        "ghostBehind" to listOf(
            "Ты впереди на {m} м. Держи темп.",
            "Преимущество {m} м — продолжай.",
        ),
        "overtook" to listOf(
            "Ты обогнал призрака! Впереди на {m} м.",
            "Обгон — {m} м преимущества.",
        ),
        "gotOvertaken" to listOf(
            "Призрак обошёл. {m} м нужно вернуть.",
            "Призрак лидирует на {m} м. Забег ещё долгий.",
        ),
        "milestone" to listOf(
            "{km} км — новая отметка в этом забеге.",
            "{km} км пройдено. Держи ритм.",
        ),
        "segmentStart" to listOf("Начало сегмента {name} — соберись!"),
        "segmentFinish" to listOf("Сегмент {name} завершён за {time}."),
        "segmentRecord" to listOf("Новый рекорд на {name}: {time}, на {gain} быстрее!"),
        "left" to listOf("Поворот налево через {m} м."),
        "right" to listOf("Поворот направо через {m} м."),
        "slightLeft" to listOf("Плавно влево через {m} м."),
        "slightRight" to listOf("Плавно вправо через {m} м."),
        "sharpLeft" to listOf("Резкий поворот налево через {m} м."),
        "sharpRight" to listOf("Резкий поворот направо через {m} м."),
        "uturn" to listOf("Разворот через {m} м."),
        "offRoute" to listOf("Ты сошёл с маршрута — вернись на линию."),
        "finish" to listOf(
            "Готово: {km} км за {time}. Хорошая тренировка.",
            "{km} км за {time} — достойная работа.",
        ),
    ),
    Locale.TR to mapOf(
        "previousBest" to listOf(
            "Son sefer: {km} km {time}. Bugün sakin başla ve tempoyu kur.",
            "{km} km {time} senin referansın. Adım adım geçeceksin.",
        ),
        "ahead" to listOf(
            "Hayaletinden {s} sn öndesin — bu tempoyu koru.",
            "{s} sn öndesin. Sakin kal.",
        ),
        "behind" to listOf(
            "{s} sn geridesin. Tempoyu yavaşça yükselt.",
            "{s} sn kapatılacak — biraz daha güçlü bir adım yeter.",
        ),
        "ghostAhead" to listOf(
            "Hayalet {m} m önde. Metre metre kapat.",
            "{m} m kaldı.",
        ),
        "ghostBehind" to listOf(
            "{m} m öndesin. Tempoyu koru.",
            "{m} m avantaj — böyle devam.",
        ),
        "overtook" to listOf(
            "Hayaletini geçtin! {m} m öndesin.",
            "Geçiş tamam — {m} m önde.",
        ),
        "gotOvertaken" to listOf(
            "Hayalet geçti. {m} m kapatılacak.",
            "Hayalet {m} m önde. Yarış uzun.",
        ),
        "milestone" to listOf(
            "{km} km — bu seferde yeni bir eşik.",
            "{km} km tamam. Bu ritmi koru.",
        ),
        "segmentStart" to listOf("{name} segmenti başlıyor — odaklan!"),
        "segmentFinish" to listOf("{name} segmenti {time} ile bitti."),
        "segmentRecord" to listOf("{name} rekoru: {time}, {gain} daha hızlı!"),
        "left" to listOf("{m} m sonra sola dön."),
        "right" to listOf("{m} m sonra sağa dön."),
        "slightLeft" to listOf("{m} m sonra hafif solda kal."),
        "slightRight" to listOf("{m} m sonra hafif sağda kal."),
        "sharpLeft" to listOf("{m} m sonra keskin sol."),
        "sharpRight" to listOf("{m} m sonra keskin sağ."),
        "uturn" to listOf("{m} m sonra geri dön."),
        "offRoute" to listOf("Rotadan çıktın — çizgiye dön."),
        "finish" to listOf(
            "Bitti: {km} km {time}. İyi sefer.",
            "{km} km {time} — sağlam iş.",
        ),
    ),
    Locale.ID to mapOf(
        "previousBest" to listOf(
            "Terakhir: {km} km dalam {time}. Hari ini mulai stabil dan bangun irama.",
            "{km} km dalam {time} adalah patokanmu. Sedikit demi sedikit kau akan mengalahkannya.",
        ),
        "ahead" to listOf(
            "{s} dtk di depan hantumu — jaga irama ini.",
            "Kau {s} dtk di depan. Tetap tenang.",
        ),
        "behind" to listOf(
            "{s} dtk tertinggal. Naikkan irama perlahan.",
            "{s} dtk lagi — satu langkah lebih kuat cukup.",
        ),
        "ghostAhead" to listOf(
            "Hantu {m} m di depan. Dekati meter demi meter.",
            "Tersisa {m} m untuk mengejar.",
        ),
        "ghostBehind" to listOf(
            "Kau {m} m di depan. Jaga irama.",
            "Unggul {m} m — teruskan.",
        ),
        "overtook" to listOf(
            "Kau menyalip hantumu! {m} m di depan.",
            "Salip bersih — {m} m unggul.",
        ),
        "gotOvertaken" to listOf(
            "Hantu menyalip. {m} m untuk dikejar.",
            "Hantu unggul {m} m. Lari masih panjang.",
        ),
        "milestone" to listOf(
            "{km} km — penanda baru di sesi ini.",
            "{km} km selesai. Jaga ritme.",
        ),
        "segmentStart" to listOf("Segmen {name} dimulai — fokus!"),
        "segmentFinish" to listOf("Segmen {name} selesai dalam {time}."),
        "segmentRecord" to listOf("Rekor baru di {name}: {time}, {gain} lebih cepat!"),
        "left" to listOf("Belok kiri dalam {m} m."),
        "right" to listOf("Belok kanan dalam {m} m."),
        "slightLeft" to listOf("Agak ke kiri dalam {m} m."),
        "slightRight" to listOf("Agak ke kanan dalam {m} m."),
        "sharpLeft" to listOf("Belok tajam ke kiri dalam {m} m."),
        "sharpRight" to listOf("Belok tajam ke kanan dalam {m} m."),
        "uturn" to listOf("Putar balik dalam {m} m."),
        "offRoute" to listOf("Kau keluar jalur — kembali ke garis."),
        "finish" to listOf(
            "Selesai: {km} km dalam {time}. Sesi bagus.",
            "{km} km dalam {time} — kerja solid.",
        ),
    ),
    Locale.HI to mapOf(
        "previousBest" to listOf(
            "पिछली बार: {km} किमी {time} में। आज संयम से शुरू करें और लय बनाएं।",
            "{km} किमी {time} में आपका मापदंड है। कदम दर कदम इसे पार करेंगे।",
        ),
        "ahead" to listOf(
            "अपने साये से {s} सेकंड आगे — यही लय बनाए रखें।",
            "आप {s} सेकंड आगे हैं। शांत रहें।",
        ),
        "behind" to listOf(
            "{s} सेकंड पीछे। लय धीरे-धीरे बढ़ाएं।",
            "{s} सेकंड की कमी — एक मजबूत कदम काफी है।",
        ),
        "ghostAhead" to listOf(
            "साया {m} मीटर आगे है। मीटर-दर-मीटर घटाएं।",
            "{m} मीटर बाकी है।",
        ),
        "ghostBehind" to listOf(
            "आप {m} मीटर आगे हैं। लय बनाए रखें।",
            "{m} मीटर की बढ़त — ऐसे ही चलते रहें।",
        ),
        "overtook" to listOf(
            "आपने साये को पीछे छोड़ा! {m} मीटर आगे।",
            "सफल ओवरटेक — {m} मीटर की बढ़त।",
        ),
        "gotOvertaken" to listOf(
            "साया आगे निकल गया। {m} मीटर पीछे।",
            "साया {m} मीटर आगे है। दौड़ लंबी है।",
        ),
        "milestone" to listOf(
            "{km} किमी — इस दौड़ में नया पड़ाव।",
            "{km} किमी पूरे। यही लय रखें।",
        ),
        "segmentStart" to listOf("खंड {name} शुरू — ध्यान लगाएं!"),
        "segmentFinish" to listOf("खंड {name} {time} में पूरा।"),
        "segmentRecord" to listOf("{name} पर नया रिकॉर्ड: {time}, {gain} तेज!"),
        "left" to listOf("{m} मीटर बाद बाएं मुड़ें।"),
        "right" to listOf("{m} मीटर बाद दाएं मुड़ें।"),
        "slightLeft" to listOf("{m} मीटर बाद हल्का बाएं रहें।"),
        "slightRight" to listOf("{m} मीटर बाद हल्का दाएं रहें।"),
        "sharpLeft" to listOf("{m} मीटर बाद तेज बाएं मोड़।"),
        "sharpRight" to listOf("{m} मीटर बाद तेज दाएं मोड़।"),
        "uturn" to listOf("{m} मीटर बाद वापस मुड़ें।"),
        "offRoute" to listOf("आप मार्ग से हट गए — तय रेखा पर लौटें।"),
        "finish" to listOf(
            "पूरा: {km} किमी {time} में। अच्छी दौड़।",
            "{km} किमी {time} में — ठोस मेहनत।",
        ),
    ),
    Locale.JA to mapOf(
        "previousBest" to listOf(
            "前回: {km} km を {time}。今日は落ち着いて入り、ペースを作ろう。",
            "{km} km {time} があなたの基準。一歩ずつ超えていこう。",
        ),
        "ahead" to listOf(
            "ゴーストに {s} 秒リード — このペースを保とう。",
            "{s} 秒先行。落ち着いて。",
        ),
        "behind" to listOf(
            "{s} 秒ビハインド。少しずつペースを上げよう。",
            "{s} 秒差 — もう一段強いストライドで。",
        ),
        "ghostAhead" to listOf(
            "ゴーストが {m} m 先行。1 m ずつ詰めよう。",
            "あと {m} m。",
        ),
        "ghostBehind" to listOf(
            "{m} m リード。ペースを維持。",
            "{m} m 先行 — その調子。",
        ),
        "overtook" to listOf(
            "ゴーストを抜いた！ {m} m リード。",
            "パス成功 — {m} m 先行。",
        ),
        "gotOvertaken" to listOf(
            "ゴーストに抜かれた。{m} m 差。",
            "ゴーストが {m} m 先行。まだ長い。",
        ),
        "milestone" to listOf(
            "{km} km — このランでの新しい節目。",
            "{km} km 完了。このリズムを保とう。",
        ),
        "segmentStart" to listOf("セグメント {name} 開始 — 集中！"),
        "segmentFinish" to listOf("セグメント {name} を {time} で完走。"),
        "segmentRecord" to listOf("{name} の新記録: {time}、{gain} 短縮！"),
        "left" to listOf("{m} m 先を左折。"),
        "right" to listOf("{m} m 先を右折。"),
        "slightLeft" to listOf("{m} m 先はやや左。"),
        "slightRight" to listOf("{m} m 先はやや右。"),
        "sharpLeft" to listOf("{m} m 先を大きく左折。"),
        "sharpRight" to listOf("{m} m 先を大きく右折。"),
        "uturn" to listOf("{m} m 先で U ターン。"),
        "offRoute" to listOf("ルートを外れています — 線に戻ってください。"),
        "finish" to listOf(
            "完了: {km} km を {time}。いいランでした。",
            "{km} km {time} — 確かな走り。",
        ),
    ),
    Locale.KO to mapOf(
        "previousBest" to listOf(
            "지난번: {km} km {time}. 오늘은 차분히 시작해 페이스를 만들자.",
            "{km} km {time}이 기준이다. 한 걸음씩 넘어서자.",
        ),
        "ahead" to listOf(
            "고스트보다 {s}초 앞선다 — 이 페이스를 유지해.",
            "{s}초 앞서 있다. 침착하게.",
        ),
        "behind" to listOf(
            "{s}초 뒤진다. 페이스를 조금씩 올려.",
            "{s}초 차이 — 한 번 더 힘찬 발걸음이면 된다.",
        ),
        "ghostAhead" to listOf(
            "고스트가 {m}m 앞선다. 1m씩 좁혀라.",
            "{m}m 남았다.",
        ),
        "ghostBehind" to listOf(
            "{m}m 앞선다. 페이스를 유지해.",
            "{m}m 앞섰다 — 계속 가자.",
        ),
        "overtook" to listOf(
            "고스트를 추월했다! {m}m 앞선다.",
            "추월 성공 — {m}m 앞선다.",
        ),
        "gotOvertaken" to listOf(
            "고스트가 앞질렀다. {m}m 남았다.",
            "고스트가 {m}m 앞선다. 경기는 길다.",
        ),
        "milestone" to listOf(
            "{km} km — 이번 러닝의 새 이정표.",
            "{km} km 완료. 이 리듬을 유지해.",
        ),
        "segmentStart" to listOf("세그먼트 {name} 시작 — 집중!"),
        "segmentFinish" to listOf("세그먼트 {name} {time}에 완료."),
        "segmentRecord" to listOf("{name} 신기록: {time}, {gain} 단축!"),
        "left" to listOf("{m}m 뒤 왼쪽으로."),
        "right" to listOf("{m}m 뒤 오른쪽으로."),
        "slightLeft" to listOf("{m}m 뒤 왼쪽으로 살짝."),
        "slightRight" to listOf("{m}m 뒤 오른쪽으로 살짝."),
        "sharpLeft" to listOf("{m}m 뒤 급좌회전."),
        "sharpRight" to listOf("{m}m 뒤 급우회전."),
        "uturn" to listOf("{m}m 뒤 유턴."),
        "offRoute" to listOf("경로를 벗어났다 — 계획선으로 돌아가."),
        "finish" to listOf(
            "완료: {km} km {time}. 좋은 러닝이었다.",
            "{km} km {time} — 탄탄한 훈련.",
        ),
    ),
    Locale.ZH to mapOf(
        "previousBest" to listOf(
            "上次：{km} 公里 {time}。今天稳着起步，慢慢建立节奏。",
            "{km} 公里 {time} 是你的标杆。一步一步就能超越。",
        ),
        "ahead" to listOf(
            "领先幽灵 {s} 秒——保持这个节奏。",
            "你领先 {s} 秒。稳住。",
        ),
        "behind" to listOf(
            "落后 {s} 秒。慢慢提速。",
            "还差 {s} 秒——再加一把劲。",
        ),
        "ghostAhead" to listOf(
            "幽灵领先 {m} 米。一米一米追。",
            "还差 {m} 米。",
        ),
        "ghostBehind" to listOf(
            "你领先 {m} 米。保持节奏。",
            "领先 {m} 米——继续。",
        ),
        "overtook" to listOf(
            "超过幽灵了！领先 {m} 米。",
            "完成超越——领先 {m} 米。",
        ),
        "gotOvertaken" to listOf(
            "幽灵超过去了。还差 {m} 米。",
            "幽灵领先 {m} 米。比赛还长。",
        ),
        "milestone" to listOf(
            "{km} 公里——本次新的里程碑。",
            "{km} 公里完成。保持节奏。",
        ),
        "segmentStart" to listOf("路段 {name} 开始——专注！"),
        "segmentFinish" to listOf("路段 {name} 用时 {time} 完成。"),
        "segmentRecord" to listOf("{name} 新纪录：{time}，快了 {gain}！"),
        "left" to listOf("{m} 米后左转。"),
        "right" to listOf("{m} 米后右转。"),
        "slightLeft" to listOf("{m} 米后稍向左。"),
        "slightRight" to listOf("{m} 米后稍向右。"),
        "sharpLeft" to listOf("{m} 米后急左转。"),
        "sharpRight" to listOf("{m} 米后急右转。"),
        "uturn" to listOf("{m} 米后掉头。"),
        "offRoute" to listOf("已偏离路线——回到规划线路。"),
        "finish" to listOf(
            "完成：{km} 公里 {time}。不错的训练。",
            "{km} 公里 {time}——扎实的付出。",
        ),
    ),
)
