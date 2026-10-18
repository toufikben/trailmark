package dev.toufik.trailmark.app

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
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

    fun connect() {
        if (client != null) return
        val c = BillingClient.newBuilder(appContext)
            .enablePendingPurchases()
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
                    queryProducts()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
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
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
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
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases)
        }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases)
        }
    }

    fun buy(activity: Activity, productId: String) {
        val c = client ?: return
        val d = details[productId] ?: return
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
            if (!p.isAcknowledged) {
                client?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken)
                        .build(),
                ) { _ -> }
            }
            entitlements.onPurchase(id)
        }
    }
}

val LocalBilling = compositionLocalOf<BillingManager?> { null }
