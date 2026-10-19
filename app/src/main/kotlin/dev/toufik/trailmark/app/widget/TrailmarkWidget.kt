package dev.toufik.trailmark.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.toufik.trailmark.R
import dev.toufik.trailmark.app.MainActivity
import java.util.Locale

/**
 * Home-screen widget: the last finished activity's sport, distance and time.
 * The data is written to preferences by the recording service on finish; the
 * widget only renders it and opens the app on tap.
 */
class TrailmarkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    companion object {

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
            val distM = prefs.getFloat("last_dist_m", -1f)
            if (distM < 0f) {
                views.setTextViewText(R.id.widget_value, "—")
                views.setTextViewText(R.id.widget_sub, context.getString(R.string.app_name))
            } else {
                val imperial = prefs.getBoolean("units_imperial", false)
                val dist = if (imperial) distM / 1609.344 else distM / 1000.0
                val unit = if (imperial) "mi" else "km"
                val secs = prefs.getLong("last_time_s", 0L)
                val h = secs / 3600
                val m = (secs % 3600) / 60
                val s = secs % 60
                val time = if (h > 0) {
                    String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                } else {
                    String.format(Locale.US, "%d:%02d", m, s)
                }
                val sport = prefs.getString("last_sport", "") ?: ""
                views.setTextViewText(
                    R.id.widget_value,
                    String.format(Locale.US, "%.2f %s", dist, unit),
                )
                views.setTextViewText(R.id.widget_sub, "$sport · $time")
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
    }
}
