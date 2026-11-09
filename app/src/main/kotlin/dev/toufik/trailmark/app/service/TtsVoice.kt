package dev.toufik.trailmark.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import dev.toufik.trailmark.app.Lang
import java.util.Locale as JavaLocale

/**
 * One place that decides which system voice speaks the coach, and how deep it
 * sounds.
 *
 * Android has no "male/female" switch: the engine ships several voices per
 * language and the app can only choose among the installed ones. So the coach
 * exposes both: a pitch control (works on every engine, lowers a female default
 * into a male-sounding range) and a picker over the engine's own voices when
 * the device actually has more than one.
 */
object TtsVoice {
    private const val PREFS = "trailmark"
    const val KEY_NAME = "tts_voice_name"
    const val KEY_PITCH = "tts_pitch"

    /** Pitch levels offered in settings; index 1 is the engine default. */
    val PITCH_LEVELS = floatArrayOf(0.75f, 1.0f, 1.25f)

    fun localeOf(lang: Lang): JavaLocale = JavaLocale.forLanguageTag(lang.code)

    /** Installed offline voices for [lang], best quality first. */
    fun available(tts: TextToSpeech, lang: Lang): List<Voice> {
        val code = localeOf(lang).language
        return runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == code && !it.isNetworkConnectionRequired }
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
        }.getOrElse { emptyList() }
    }

    /**
     * Applies the saved voice and pitch, falling back to the engine default for
     * the language when the saved voice disappeared (engine update, new phone).
     */
    fun apply(tts: TextToSpeech, context: Context, lang: Lang) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching { tts.language = localeOf(lang) }
        val pitch = prefs.getFloat(KEY_PITCH, 1.0f).coerceIn(0.5f, 1.6f)
        runCatching { tts.setPitch(pitch) }
        val name = prefs.getString(KEY_NAME, null)
        if (!name.isNullOrBlank()) {
            val voice = runCatching { tts.voices?.firstOrNull { it.name == name } }.getOrNull()
            if (voice != null) runCatching { tts.voice = voice }
        }
    }

    /** Human label for a voice row: locale, quality and a gender-ish hint when present. */
    fun label(voice: Voice): String {
        val q = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "\u2605\u2605\u2605"
            voice.quality >= Voice.QUALITY_HIGH -> "\u2605\u2605"
            else -> "\u2605"
        }
        val short = voice.name.substringAfterLast('-').substringBefore('_')
        return "${voice.locale.toLanguageTag()} \u00b7 $q \u00b7 $short"
    }

    /**
     * The engine's installed voices for [lang] as `name to label`. A throwaway
     * engine is created and shut down: the settings screen only needs the list,
     * the recording service keeps its own instance.
     */
    suspend fun names(context: Context, lang: Lang): List<Pair<String, String>> {
        val engine = TextToSpeech(context.applicationContext) { }
        return try {
            val deadline = System.currentTimeMillis() + 2500
            while (System.currentTimeMillis() < deadline && engine.voices.isNullOrEmpty()) {
                kotlinx.coroutines.delay(120)
            }
            available(engine, lang).map { it.name to label(it) }
        } catch (t: Throwable) {
            emptyList()
        } finally {
            runCatching { engine.shutdown() }
        }
    }
}
