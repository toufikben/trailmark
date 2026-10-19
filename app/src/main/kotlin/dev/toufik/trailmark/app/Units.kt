package dev.toufik.trailmark.app

import android.content.Context

/** Display units: metric (km/m) by default, imperial (mi/ft) opt-in from settings. */
object Units {
    @Volatile var imperial: Boolean = false

    fun init(context: Context) {
        imperial = context
            .getSharedPreferences("trailmark", Context.MODE_PRIVATE)
            .getBoolean("units_imperial", false)
    }

    fun set(context: Context, imperialNow: Boolean) {
        imperial = imperialNow
        context.getSharedPreferences("trailmark", Context.MODE_PRIVATE)
            .edit().putBoolean("units_imperial", imperialNow).apply()
    }
}
