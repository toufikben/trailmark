package dev.toufik.trailmark.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.toufik.trailmark.R
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.MainActivity
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.textsFor
import java.util.Locale

/**
 * Home-screen widget. While a run is going it shows the live distance and time;
 * when nothing is being recorded it falls back to the last finished activity.
 * The service writes those numbers to preferences every few seconds, and the
 * widget only renders them, so it never touches the database from a broadcast.
 */
class TrailmarkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    companion object {

        /** Preferences the recording service writes for the live branch. */
        const val KEY_LIVE_ACTIVE = "live_active"
        const val KEY_LIVE_DIST = "live_dist_m"
        const val KEY_LIVE_TIME = "live_time_s"
        const val KEY_LIVE_SPORT = "live_sport"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TrailmarkWidget::class.java),
            )
            if (ids.isEmpty()) return
            val views = build(context)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences("trailmark", Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_trailmark)
            val live = prefs.getBoolean(KEY_LIVE_ACTIVE, false)
            val distM = if (live) {
                prefs.getFloat(KEY_LIVE_DIST, 0f)
            } else {
                prefs.getFloat("last_dist_m", -1f)
            }
            if (distM < 0f) {
                views.setTextViewText(R.id.widget_value, "\u2014")
                views.setTextViewText(R.id.widget_sub, context.getString(R.string.app_name))
            } else {
                val imperial = prefs.getBoolean("units_imperial", false)
                val dist = if (imperial) distM / 1609.344 else distM / 1000.0
                val unit = if (imperial) "mi" else "km"
                val secs = if (live) {
                    prefs.getLong(KEY_LIVE_TIME, 0L)
                } else {
                    prefs.getLong("last_time_s", 0L)
                }
                val h = secs / 3600
                val m = (secs % 3600) / 60
                val s = secs % 60
                val time = if (h > 0) {
                    String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                } else {
                    String.format(Locale.US, "%d:%02d", m, s)
                }
                val sport = prefs.getString(if (live) KEY_LIVE_SPORT else "last_sport", "") ?: ""
                val label = sportLabel(context, sport)
                views.setTextViewText(
                    R.id.widget_value,
                    String.format(Locale.US, "%.2f %s", dist, unit),
                )
                val prefix = if (live) "\u25cf " else ""
                views.setTextViewText(R.id.widget_sub, "$prefix$label \u00b7 $time")
            }
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }

        /** Sport names are stored as enum names; the widget shows the app language. */
        private fun sportLabel(context: Context, sport: String): String {
            val code = context.getSharedPreferences("trailmark", Context.MODE_PRIVATE)
                .getString("lang_code", null)
            val lang = Lang.entries.firstOrNull { it.code == code } ?: Lang.AR
            return labelFor(textsFor(lang), sport)
        }

        private fun labelFor(t: Texts, sport: String): String = when (sport) {
            "RUN" -> t.run
            "WALK" -> t.walk
            "BIKE" -> t.bike
            "HIKE" -> t.hike
            else -> sport
        }
    }
}
