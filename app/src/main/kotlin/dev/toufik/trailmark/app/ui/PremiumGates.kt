package dev.toufik.trailmark.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.trialClock
import kotlinx.coroutines.delay

/**
 * True while the 15-day trial is running or a store purchase is active.
 *
 * The clock is the stored high-water mark, not the live one, so moving the
 * device clock back cannot restart the trial. Re-evaluates every 30 seconds so
 * a screen that stays open across the trial boundary locks itself without
 * needing a restart.
 */
@Composable
fun rememberPro(): Boolean {
    val premium = LocalPremium.current
    val context = LocalContext.current
    var now by remember(premium) { mutableStateOf(trialClock(context)) }
    LaunchedEffect(premium) {
        while (true) {
            delay(30_000L)
            now = trialClock(context)
        }
    }
    return premium?.isPro(now) == true
}
