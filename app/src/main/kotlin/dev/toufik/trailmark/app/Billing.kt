package dev.toufik.trailmark.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.compositionLocalOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Play Billing wrapper: monthly / yearly subscriptions and a lifetime purchase. */
class BillingManager(
    context: Context,
    private val entitlements: Entitlements,
) {
    private val appContext = context.applicationContext
    private var client: BillingClient? = null
    private var details: Map<String, ProductDetails> = emptyMap()

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var retryCount = 0

    fun connect() {
        if (client != null) return
        val c = BillingClient.newBuilder(appContext)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    handle(purchases)
                }
            }
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryCount = 0
                    _lastError.value = null
                    queryProducts()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                client = null
                retryConnect()
            }
        })
    }

    private fun retryConnect() {
        if (retryCount >= 5) return
        retryCount += 1
        Handler(Looper.getMainLooper()).postDelayed({ connect() }, 1_000L * retryCount)
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun queryProducts() {
        val c = client ?: return
        val products = PremiumProducts.ALL.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(
                    if (PremiumProducts.isSubscription(id)) BillingClient.ProductType.SUBS
                    else BillingClient.ProductType.INAPP,
                )
                .build()
        }
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val list = queryResult.productDetailsList
            details = list.associateBy { it.productId }
            _prices.value = list.associate { d -> d.productId to priceOf(d) }
        }
    }

    private fun priceOf(d: ProductDetails): String {
        d.oneTimePurchaseOfferDetails?.let { return it.formattedPrice }
        val phase = d.subscriptionOfferDetails
            ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
        return phase?.formattedPrice ?: ""
    }

    fun refreshPurchases() {
        val c = client ?: return
        val found = Collections.synchronizedSet(mutableSetOf<String>())
        val pending = AtomicInteger(2)
        fun finish() {
            if (pending.decrementAndGet() == 0) entitlements.syncFromStore(found.toSet())
        }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                collectActive(purchases, found)
                handle(purchases)
            }
            finish()
        }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                collectActive(purchases, found)
                handle(purchases)
            }
            finish()
        }
    }

    private fun collectActive(purchases: List<Purchase>, out: MutableSet<String>) {
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            p.products.firstOrNull()
                ?.takeIf { PremiumProducts.ALL.contains(it) }
                ?.let { out.add(it) }
        }
    }

    fun buy(activity: Activity, productId: String) {
        val c = client
        if (c == null) {
            _lastError.value = "store"
            retryConnect()
            return
        }
        val d = details[productId]
        if (d == null) {
            _lastError.value = "product"
            queryProducts()
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d)
            .apply {
                if (PremiumProducts.isSubscription(productId)) {
                    val offer = d.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    if (offer != null) setOfferToken(offer)
                }
            }
            .build()
        c.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build(),
        )
    }

    private fun handle(purchases: List<Purchase>) {
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val id = p.products.firstOrNull() ?: continue
            if (!PremiumProducts.ALL.contains(id)) continue
            if (p.isAcknowledged) {
                entitlements.onPurchase(id)
            } else {
                client?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken)
                        .build(),
                ) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        entitlements.onPurchase(id)
                    } else {
                        _lastError.value = "ack"
                    }
                }
            }
        }
    }
}

val LocalBilling = compositionLocalOf<BillingManager?> { null }
