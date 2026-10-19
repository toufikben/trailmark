package dev.toufik.trailmark.app

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val TRIAL_DAYS = 15
const val FREE_OFFLINE_AREAS = 1

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
) {
    fun trialDaysLeft(now: Long): Int {
        val end = trialStartedAt + TRIAL_DAYS * 86_400_000L
        val left = end - now
        if (left <= 0) return 0
        return ((left + 86_399_999L) / 86_400_000L).toInt().coerceAtMost(TRIAL_DAYS)
    }

    fun inTrial(now: Long): Boolean = trialDaysLeft(now) > 0

    fun isPro(now: Long): Boolean = purchasedProduct != null || inTrial(now)
}

class Entitlements(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("trailmark", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<PremiumState> = _state

    private fun load(): PremiumState {
        var start = prefs.getLong("trial_start", 0L)
        if (start <= 0L) {
            start = System.currentTimeMillis()
            prefs.edit().putLong("trial_start", start).apply()
        }
        return PremiumState(start, prefs.getString("pro_product", null))
    }

    fun onPurchase(productId: String) {
        val current = _state.value.purchasedProduct
        val keep = when {
            current == PremiumProducts.LIFETIME || productId == PremiumProducts.LIFETIME ->
                PremiumProducts.LIFETIME
            else -> productId
        }
        prefs.edit().putString("pro_product", keep).apply()
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
        if (best == null) prefs.edit().remove("pro_product").apply()
        else prefs.edit().putString("pro_product", best).apply()
        _state.value = _state.value.copy(purchasedProduct = best)
    }
}

val LocalPremium = compositionLocalOf<PremiumState?> { null }
