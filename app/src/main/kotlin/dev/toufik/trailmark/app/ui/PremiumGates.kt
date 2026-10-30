package dev.toufik.trailmark.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.toufik.trailmark.app.LocalPremium
import kotlinx.coroutines.delay

/**
 * True while the 15-day trial is running or a store purchase is active.
 *
 * Re-evaluates every 30 seconds so a screen that stays open across the trial
 * boundary locks itself without needing a restart.
 */
@Composable
fun rememberPro(): Boolean {
    val premium = LocalPremium.current
    var now by remember(premium) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(premium) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    return premium?.isPro(now) == true
}
