package dev.toufik.trailmark.app.ui

import android.content.Context
import dev.toufik.trailmark.app.isProNow

/**
 * The shape of the live position marker. The arrow is the free default; every
 * other shape is part of Pro.
 *
 * The choice is stored by key, so a style picked during the trial comes back if
 * Pro is bought later, while a lapsed trial falls back to the arrow instead of
 * keeping a paid shape.
 */
enum class PuckStyle(val key: String, val pro: Boolean) {
    ARROW("arrow", false),
    RUNNER("runner", true),
    BICYCLE("bicycle", true),
    STAR("star", true),
    RINGS("rings", true),
    FLAME("flame", true),
    COMPASS("compass", true),
    COMET("comet", true),
    PHOENIX("phoenix", true),
    AURORA("aurora", true),
    SHOOTING_STAR("shooting_star", true),
    SPARK("spark", true),
    INK("ink", true);

    /**
     * True when the shape paints a trail on the recording map and in the video,
     * replacing the flat accent line behind the marker.
     */
    val hasTrail: Boolean
        get() = this == COMET || this == PHOENIX || this == AURORA ||
            this == SHOOTING_STAR || this == SPARK || this == INK

    /**
     * True when the art glows past its casing. Such a sprite is rendered on a
     * larger bitmap and scaled back down, so the glow is not clipped.
     */
    val wideArt: Boolean
        get() = hasTrail

    companion object {
        const val KEY = "puck_style"

        fun fromKey(key: String?): PuckStyle = entries.firstOrNull { it.key == key } ?: ARROW

        /** The style the recorder and the exporters are allowed to draw right now. */
        fun current(context: Context): PuckStyle {
            val prefs = context.getSharedPreferences("trailmark", Context.MODE_PRIVATE)
            val chosen = fromKey(prefs.getString(KEY, null))
            if (chosen.pro && !isProNow(context)) return ARROW
            return chosen
        }
    }
}
