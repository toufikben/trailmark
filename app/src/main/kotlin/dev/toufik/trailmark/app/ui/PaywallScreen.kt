package dev.toufik.trailmark.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.LocalBilling
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.PremiumProducts
import dev.toufik.trailmark.app.TRIAL_DAYS
import dev.toufik.trailmark.app.trialClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Monthly / yearly subscription and a lifetime purchase, with the 15-day full
 * trial shown as a status, never as a blocker: every plan can be bought while
 * the trial is still running and prices come straight from Play.
 */
@Composable
fun PaywallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val lang = LocalLang.current
    val premium = LocalPremium.current
    val billing = LocalBilling.current
    val emptyPrices = remember { MutableStateFlow(emptyMap<String, String>()) }
    val prices by (billing?.prices ?: emptyPrices).collectAsState()
    val emptyError = remember { MutableStateFlow<String?>(null) }
    val billingError by (billing?.lastError ?: emptyError).collectAsState()
    var now by remember { mutableStateOf(trialClock(context)) }

    LaunchedEffect(Unit) {
        billing?.refresh()
        while (true) {
            now = trialClock(context)
            delay(60_000L)
        }
    }

    fun pw(key: String): String = paywallText(lang, key)

    val active = premium?.purchasedProduct != null
    val daysLeft = premium?.trialDaysLeft(now) ?: 0
    val status = when {
        active -> pw("proActive")
        daysLeft > 0 -> pw("trialLeft").replace("%d", daysLeft.toString())
        else -> pw("trialEnded")
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(t.back, color = Palette.accent) }
                Text(pw("title"), color = Palette.text, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                ProBadge()
            }
            Spacer(Modifier.height(8.dp))
            Text(pw("subtitle"), color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            StatusCard(
                status = status,
                active = active,
                daysLeft = daysLeft,
                subtitle = pw("onlyPro"),
            )

            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProFeature(t.ghost)
                ProFeature(t.exportVideo)
                ProFeature(t.mapRegions)
                ProFeature(t.offlineRouting)
            }

            Spacer(Modifier.height(16.dp))
            PlanCard(
                label = pw("monthly"),
                price = prices[PremiumProducts.MONTHLY]?.let { it + pw("perMonth") },
                priceMissing = pw("priceUnavailable"),
                badge = null,
                buyLabel = pw("buy"),
                onBuy = { buy(context, billing, PremiumProducts.MONTHLY) },
            )
            PlanCard(
                label = pw("yearly"),
                price = prices[PremiumProducts.YEARLY]?.let { it + pw("perYear") },
                priceMissing = pw("priceUnavailable"),
                badge = pw("best"),
                buyLabel = pw("buy"),
                onBuy = { buy(context, billing, PremiumProducts.YEARLY) },
            )
            PlanCard(
                label = pw("lifetime"),
                price = prices[PremiumProducts.LIFETIME],
                priceMissing = pw("priceUnavailable"),
                badge = pw("once"),
                buyLabel = pw("buy"),
                onBuy = { buy(context, billing, PremiumProducts.LIFETIME) },
            )

            if (prices.size < PremiumProducts.ALL.size) {
                TextButton(
                    onClick = { billing?.refresh() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(pw("retry"), color = Palette.accent, fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    billing?.clearError()
                    billing?.refreshPurchases()
                }) {
                    Text(pw("restore"), color = Palette.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                if (billingError != null) {
                    TextButton(onClick = { billing?.refresh() }) {
                        Text(t.loadFailed, color = Palette.dangerText, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProBadge() {
    Text(
        text = "PRO",
        color = Color(0xFF241A00),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.gold)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun StatusCard(status: String, active: Boolean, daysLeft: Int, subtitle: String) {
    Surface(color = Palette.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (active) Palette.gold else Palette.accent),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    status,
                    color = if (active) Palette.gold else Palette.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!active && daysLeft > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { 1f - daysLeft.toFloat() / TRIAL_DAYS },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Palette.accent,
                    trackColor = Palette.bg,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Palette.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProFeature(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✓", color = Palette.accent, fontSize = 14.sp)
        Spacer(Modifier.size(8.dp))
        Text(label, color = Palette.text, fontSize = 13.sp)
    }
}

private fun buy(
    context: android.content.Context,
    billing: dev.toufik.trailmark.app.BillingManager?,
    productId: String,
) {
    (context as? Activity)?.let { billing?.buy(it, productId) }
}

@Composable
private fun PlanCard(
    label: String,
    price: String?,
    priceMissing: String,
    badge: String?,
    buyLabel: String,
    onBuy: () -> Unit,
) {
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = Palette.text, fontSize = 16.sp)
                    if (badge != null) {
                        Text(badge, color = Palette.accent, fontSize = 11.sp)
                    }
                }
                Text(
                    text = price ?: priceMissing,
                    color = if (price != null) Palette.text else Palette.textSecondary,
                    fontSize = if (price != null) 18.sp else 12.sp,
                    fontWeight = if (price != null) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onBuy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.accent,
                    contentColor = Palette.onAccent,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) { Text(buyLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
