package dev.toufik.trailmark.app

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks for a Play review only after genuine use: three finished activities of
 * at least five minutes and 500 m, with at least 60 days between prompts.
 * Play applies its own quota on top of this.
 */
object ReviewPrompt {
    private const val PREFS = "trailmark"
    private const val KEY_COUNT = "review_completed_count"
    private const val KEY_LAST_ACTIVITY = "review_last_activity_id"
    private const val KEY_LAST_PROMPT = "review_last_prompt_millis"
    private const val MIN_ACTIVITIES = 3
    private const val MIN_DISTANCE_M = 500.0
    private const val MIN_ELAPSED_NANOS = 5L * 60L * 1_000_000_000L
    private const val COOLDOWN_MILLIS = 60L * 24L * 60L * 60L * 1000L

    @Volatile private var asking = false

    suspend fun onRecordingStopped(activity: Activity, activityId: Long) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_ACTIVITY, -1L) == activityId) return
        prefs.edit().putLong(KEY_LAST_ACTIVITY, activityId).apply()
        val counts = withContext(Dispatchers.IO) {
            runCatching {
                val row = AppGraph.repository(activity).activityById(activityId)
                    ?: return@runCatching false
                row.distanceM >= MIN_DISTANCE_M && row.elapsedNanos >= MIN_ELAPSED_NANOS
            }.getOrDefault(false)
        }
        if (counts) {
            prefs.edit().putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1).apply()
        }
    }

    fun maybeAsk(activity: Activity) {
        if (asking) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_COUNT, 0) < MIN_ACTIVITIES) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_PROMPT, 0L) < COOLDOWN_MILLIS) return
        asking = true
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                asking = false
                return@addOnCompleteListener
            }
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                prefs.edit().putLong(KEY_LAST_PROMPT, now).apply()
                asking = false
            }
        }
    }
}
