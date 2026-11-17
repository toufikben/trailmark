package dev.toufik.trailmark.app.export

import android.content.Context
import dev.toufik.trailmark.app.ui.FlyoverCamera
import dev.toufik.trailmark.app.ui.FlyoverOptions

/**
 * Remembers the export recipe a user chose to keep, so the usual export is one
 * confirmation instead of seven choices. Nothing is stored until the user flips
 * the switch, so the dialog still opens on the built-in defaults for everyone
 * who never asked for this.
 */
object ExportRecipe {
    private const val PREFS = "trailmark_export_recipe"
    private const val KEY_SET = "set"

    fun load(context: Context): FlyoverOptions? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_SET, false)) return null
        return FlyoverOptions(
            seconds = p.getInt("seconds", 0).coerceAtLeast(0),
            camera = p.enumOr("camera", FlyoverCamera.entries, FlyoverCamera.FLY_3D),
            media = p.enumOr("media", ExportMedia.entries, ExportMedia.OFF),
            galleryCopy = p.getBoolean("galleryCopy", true),
            pois = p.getBoolean("pois", false),
            cinematic = p.getBoolean("cinematic", true),
        )
    }

    fun save(context: Context, options: FlyoverOptions) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SET, true)
            .putInt("seconds", options.seconds)
            .putString("camera", options.camera.name)
            .putString("media", options.media.name)
            .putBoolean("galleryCopy", options.galleryCopy)
            .putBoolean("pois", options.pois)
            .putBoolean("cinematic", options.cinematic)
            .apply()
    }

    /** Forgets the saved recipe; the next export starts from the built-in defaults. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** A name that no longer parses (renamed enum value) falls back, never throws. */
    private fun <T : Enum<T>> android.content.SharedPreferences.enumOr(
        key: String,
        values: List<T>,
        fallback: T,
    ): T = values.firstOrNull { it.name == getString(key, null) } ?: fallback
}
