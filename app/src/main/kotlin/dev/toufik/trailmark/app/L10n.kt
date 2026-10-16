package dev.toufik.trailmark.app

import androidx.compose.runtime.compositionLocalOf

enum class Lang(val code: String, val rtl: Boolean) { AR("ar", true), FR("fr", false), EN("en", false) }

/** Every user-visible string, in one place, per locale. */
data class Texts(
    val appName: String,
    val record: String,
    val start: String,
    val stop: String,
    val cancel: String,
    val points: String,
    val searching: String,
    val active: String,
    val weak: String,
    val lost: String,
    val paused: String,
    val history: String,
    val regions: String,
    val details: String,
    val back: String,
    val noActivities: String,
    val recordFirst: String,
    val loadFailed: String,
    val share: String,
    val elevation: String,
    val maxSpeed: String,
    val time: String,
    val pace: String,
    val download: String,
    val installing: String,
    val installed: String,
    val delete: String,
    val mapRegions: String,
    val language: String,
)

/** Arabic (RTL) — the app's first language. */
val AR_TEXTS = Texts(
    appName = "Trailmark",
    record = "تسجيل",
    start = "ابدأ التسجيل",
    stop = "إيقاف",
    cancel = "إلغاء",
    points = "النقاط",
    searching = "جارٍ البحث عن الإشارة…",
    active = "تسجيل نشِط",
    weak = "إشارة ضعيفة",
    lost = "فقدت الإشارة",
    paused = "متوقف مؤقتاً (خمول)",
    history = "سجل الأنشطة",
    regions = "الخرائط",
    details = "تفاصيل النشاط",
    back = "رجوع ›",
    noActivities = "لا أنشطة بعد",
    recordFirst = "سجّل أول مسار ليظهر هنا",
    loadFailed = "تعذّر التحميل",
    share = "مشاركة GPX",
    elevation = "صعود",
    maxSpeed = "أقصى",
    time = "الوقت",
    pace = "الإيقاع",
    download = "تنزيل",
    installing = "…جارٍ التنزيل",
    installed = "مثبّت",
    delete = "حذف",
    mapRegions = "مناطق الخرائط",
    language = "اللغة",
)
val FR_TEXTS = Texts(
    appName = "Trailmark",
    record = "Enregistrer",
    start = "Démarrer",
    stop = "Arrêter",
    cancel = "Annuler",
    points = "Points",
    searching = "Recherche du signal…",
    active = "Enregistrement actif",
    weak = "Signal faible",
    lost = "Signal perdu",
    paused = "En pause (immobile)",
    history = "Historique",
    regions = "Cartes",
    details = "Détail de l'activité",
    back = "Retour ›",
    noActivities = "Aucune activité",
    recordFirst = "Enregistrez un parcours pour le voir ici",
    loadFailed = "Échec du chargement",
    share = "Partager GPX",
    elevation = "Dénivelé",
    maxSpeed = "Max",
    time = "Temps",
    pace = "Allure",
    download = "Télécharger",
    installing = "…téléchargement",
    installed = "Installé",
    delete = "Supprimer",
    mapRegions = "Régions de carte",
    language = "Langue",
)

val EN_TEXTS = Texts(
    appName = "Trailmark",
    record = "Record",
    start = "Start",
    stop = "Stop",
    cancel = "Cancel",
    points = "Points",
    searching = "Searching for signal…",
    active = "Recording active",
    weak = "Weak signal",
    lost = "Signal lost",
    paused = "Auto-paused (idle)",
    history = "History",
    regions = "Maps",
    details = "Activity detail",
    back = "Back ›",
    noActivities = "No activities yet",
    recordFirst = "Record a route to see it here",
    loadFailed = "Load failed",
    share = "Share GPX",
    elevation = "Elevation",
    maxSpeed = "Max",
    time = "Time",
    pace = "Pace",
    download = "Download",
    installing = "…downloading",
    installed = "Installed",
    delete = "Delete",
    mapRegions = "Map regions",
    language = "Language",
)

fun textsFor(lang: Lang): Texts = when (lang) {
    Lang.AR -> AR_TEXTS
    Lang.FR -> FR_TEXTS
    Lang.EN -> EN_TEXTS
}

val LocalTexts = compositionLocalOf { AR_TEXTS }
val LocalLang = compositionLocalOf { Lang.AR }

