package dev.toufik.trailmark.app

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val TRIAL_DAYS = 15
const val FREE_OFFLINE_AREAS = 1

private const val DAY_MS = 86_400_000L
private const val KEY_TRIAL_START = "trial_start"
private const val KEY_TRIAL_SEEN = "trial_seen"
private const val KEY_PRO_PRODUCT = "pro_product"

private fun premiumPrefs(context: Context) =
    context.applicationContext.getSharedPreferences("trailmark", Context.MODE_PRIVATE)

/**
 * The furthest wall-clock moment this install has ever observed.
 *
 * The trial end is measured from a stored start, so setting the device clock
 * back used to hand out a fresh fifteen days every time. Recording the highest
 * time ever seen — and never letting the clock fall below it — makes the trial
 * monotonic: a clock moved backwards cannot refund time already spent.
 */
fun trialClock(context: Context, now: Long = System.currentTimeMillis()): Long {
    val prefs = premiumPrefs(context)
    val seen = prefs.getLong(KEY_TRIAL_SEEN, 0L)
    val high = maxOf(seen, now)
    if (high > seen) prefs.edit().putLong(KEY_TRIAL_SEEN, high).apply()
    return high
}

object PremiumProducts {
    const val MONTHLY = "pro_monthly"
    const val YEARLY = "pro_yearly"
    const val LIFETIME = "pro_lifetime"
    val ALL = listOf(MONTHLY, YEARLY, LIFETIME)

    fun isSubscription(id: String): Boolean = id != LIFETIME
}

data class PremiumState(
    val trialStartedAt: Long,
    val purchasedProduct: String?,
    /** Highest clock ever observed; the trial is measured against this, not the live clock. */
    val seenAt: Long = trialStartedAt,
) {
    fun trialDaysLeft(now: Long): Int {
        val end = trialStartedAt + TRIAL_DAYS * DAY_MS
        val left = end - maxOf(now, seenAt)
        if (left <= 0) return 0
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtMost(TRIAL_DAYS)
    }

    fun inTrial(now: Long): Boolean = trialDaysLeft(now) > 0

    fun isPro(now: Long): Boolean = purchasedProduct != null || inTrial(now)
}

class Entitlements(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = premiumPrefs(appContext)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<PremiumState> = _state

    private fun load(): PremiumState {
        var start = prefs.getLong(KEY_TRIAL_START, 0L)
        if (start <= 0L) {
            start = System.currentTimeMillis()
            prefs.edit().putLong(KEY_TRIAL_START, start).apply()
        }
        val seen = trialClock(appContext)
        return PremiumState(start, prefs.getString(KEY_PRO_PRODUCT, null), maxOf(seen, start))
    }

    /**
     * Advances the stored high-water clock and republishes the state when it
     * moved, so a screen left open across the trial boundary locks itself.
     */
    fun tick(now: Long = System.currentTimeMillis()) {
        val high = trialClock(appContext, now)
        val current = _state.value
        if (high > current.seenAt) _state.value = current.copy(seenAt = high)
    }

    fun onPurchase(productId: String) {
        val current = _state.value.purchasedProduct
        val keep = when {
            current == PremiumProducts.LIFETIME || productId == PremiumProducts.LIFETIME ->
                PremiumProducts.LIFETIME
            else -> productId
        }
        prefs.edit().putString(KEY_PRO_PRODUCT, keep).apply()
        _state.value = _state.value.copy(purchasedProduct = keep)
    }

    /** Reconciles the stored entitlement with what the store reports; revokes when nothing is active. */
    fun syncFromStore(activeProducts: Set<String>) {
        val best = when {
            PremiumProducts.LIFETIME in activeProducts -> PremiumProducts.LIFETIME
            PremiumProducts.YEARLY in activeProducts -> PremiumProducts.YEARLY
            PremiumProducts.MONTHLY in activeProducts -> PremiumProducts.MONTHLY
            else -> null
        }
        if (best == _state.value.purchasedProduct) return
        if (best == null) prefs.edit().remove(KEY_PRO_PRODUCT).apply()
        else prefs.edit().putString(KEY_PRO_PRODUCT, best).apply()
        _state.value = _state.value.copy(purchasedProduct = best)
    }
}

val LocalPremium = compositionLocalOf<PremiumState?> { null }

/**
 * The entitlement read straight from prefs, for callers outside the activity's
 * composition — the video renderer and the map sprites run where the
 * [LocalPremium] value is not in scope. [Entitlements] writes the same keys.
 */
fun isProNow(context: Context, now: Long = System.currentTimeMillis()): Boolean {
    val prefs = premiumPrefs(context)
    if (prefs.getString(KEY_PRO_PRODUCT, null) != null) return true
    val start = prefs.getLong(KEY_TRIAL_START, 0L)
    if (start <= 0L) return false
    return trialClock(context, now) < start + TRIAL_DAYS * DAY_MS
}
