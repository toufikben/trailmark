package dev.toufik.trailmark.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.LocalBilling
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.PremiumProducts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Monthly / yearly subscription and a lifetime purchase, with a 15-day full trial. */
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
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    fun pw(key: String): String = paywallText(lang, key)

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(t.back, color = Palette.accent) }
                Text(pw("title"), color = Palette.text, fontSize = 22.sp)
            }
            Spacer(Modifier.padding(top = 10.dp))
            Text(pw("subtitle"), color = Palette.textSecondary, fontSize = 13.sp)
            val status = when {
                premium?.purchasedProduct != null -> pw("proActive")
                (premium?.trialDaysLeft(now) ?: 0) > 0 ->
                    pw("trialLeft").replace("%d", (premium?.trialDaysLeft(now) ?: 0).toString())
                else -> pw("trialEnded")
            }
            Text(
                status,
                color = Palette.accent,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.padding(top = 14.dp))
            PlanCard(
                label = pw("monthly"),
                price = prices[PremiumProducts.MONTHLY]?.let { it + pw("perMonth") },
                badge = null,
                buyLabel = pw("buy"),
                onClick = { buy(context, billing, PremiumProducts.MONTHLY) },
            )
            PlanCard(
                label = pw("yearly"),
                price = prices[PremiumProducts.YEARLY]?.let { it + pw("perYear") },
                badge = pw("best"),
                buyLabel = pw("buy"),
                onClick = { buy(context, billing, PremiumProducts.YEARLY) },
            )
            PlanCard(
                label = pw("lifetime"),
                price = prices[PremiumProducts.LIFETIME]?.let { it + " · " + pw("once") },
                badge = null,
                buyLabel = pw("buy"),
                onClick = { buy(context, billing, PremiumProducts.LIFETIME) },
            )
            Spacer(Modifier.padding(top = 8.dp))
            TextButton(onClick = {
                billing?.clearError()
                billing?.refreshPurchases()
            }) {
                Text(pw("restore"), color = Palette.textSecondary)
            }
            if (billingError != null) {
                Text(t.loadFailed, color = Palette.dangerText, fontSize = 12.sp)
            }
            Text(pw("onlyPro"), color = Palette.textSecondary, fontSize = 12.sp)
        }
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
    badge: String?,
    buyLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Palette.text, fontSize = 16.sp)
                if (badge != null) {
                    Text(badge, color = Palette.accent, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price ?: "…", color = Palette.text, fontSize = 15.sp)
                Spacer(Modifier.padding(top = 4.dp))
                Button(
                    onClick = onClick,
                    enabled = price != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.accent,
                        contentColor = Palette.onAccent,
                    ),
                ) { Text(buyLabel, fontSize = 13.sp) }
            }
        }
    }
}
