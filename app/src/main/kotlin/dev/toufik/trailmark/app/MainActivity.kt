package dev.toufik.trailmark.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.ui.DetailScreen
import dev.toufik.trailmark.app.ui.HistoryScreen
import dev.toufik.trailmark.app.ui.Motion
import dev.toufik.trailmark.app.ui.PaywallScreen
import dev.toufik.trailmark.app.ui.PlanScreen
import dev.toufik.trailmark.app.ui.ProgressScreen
import dev.toufik.trailmark.app.ui.RegionsScreen
import dev.toufik.trailmark.app.ui.SettingsScreen
import dev.toufik.trailmark.recording.Phase

sealed interface Screen {
    data object Record : Screen
    data object Plan : Screen
    data object History : Screen
    data object Progress : Screen
    data object Settings : Screen
    data object Regions : Screen
    data object Paywall : Screen
    data class Detail(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {
    private var syntheticStarted = false

    override fun onResume() {
        super.onResume()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable && !syntheticStarted && intent?.getBooleanExtra("synthetic", false) == true) {
            syntheticStarted = true
            val svc = Intent(this, dev.toufik.trailmark.app.service.RecordingService::class.java)
                .setAction(dev.toufik.trailmark.app.service.RecordingService.ACTION_START)
                .putExtra(dev.toufik.trailmark.app.service.RecordingService.EXTRA_SPORT, "RUN")
                .putExtra(dev.toufik.trailmark.app.service.RecordingService.EXTRA_SYNTHETIC, true)
            ContextCompat.startForegroundService(this, svc)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.maplibre.android.MapLibre.getInstance(this)
        val prefs = getSharedPreferences("trailmark", Context.MODE_PRIVATE)
        dev.toufik.trailmark.app.Units.init(this)
        val entitlements = Entitlements(this)
        val billing = BillingManager(this, entitlements)
        billing.connect()
        setContent {
            var lang by remember {
                mutableStateOf(
                    Lang.entries.firstOrNull { it.code == prefs.getString("lang_code", null) }
                        ?: Lang.AR,
                )
            }
            val texts = textsFor(lang)
            val direction = if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val premiumState by entitlements.state.collectAsState()

            CompositionLocalProvider(
                LocalTexts provides texts,
                LocalLang provides lang,
                LocalPremium provides premiumState,
                LocalBilling provides billing,
            ) {
                TrailmarkTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        var screen by remember { mutableStateOf<Screen>(Screen.Record) }
                        var paywallFrom by remember { mutableStateOf<Screen>(Screen.Settings) }
                        var detailFrom by remember { mutableStateOf<Screen>(Screen.History) }
                        val recState by RecordingHolder.state.collectAsState()
                        BackHandler(enabled = screen != Screen.Record) {
                            screen = when (val s = screen) {
                                is Screen.Detail -> detailFrom
                                Screen.Regions -> Screen.Plan
                                Screen.Paywall -> paywallFrom
                                else -> Screen.Record
                            }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(
                                    targetState = screen,
                                    transitionSpec = {
                                        val dir = if (tabRank(targetState) >= tabRank(initialState)) 1 else -1
                                        (fadeIn(tween(Motion.MED, easing = Motion.EaseOut)) +
                                            slideInVertically(tween(Motion.MED, easing = Motion.EaseOut)) { it / 14 * dir })
                                            .togetherWith(fadeOut(tween(Motion.FAST)))
                                    },
                                    label = "tab-content",
                                ) { s ->
                                when (s) {
                                    Screen.Record -> RecordingScreen(
                                        onOpenDetail = { id ->
                                            detailFrom = Screen.Record
                                            screen = Screen.Detail(id)
                                        },
                                    )
                                    Screen.Plan -> PlanScreen(
                                        onRegions = { screen = Screen.Regions },
                                    )
                                    Screen.History -> HistoryScreen(
                                        onOpen = { id ->
                                            detailFrom = Screen.History
                                            screen = Screen.Detail(id)
                                        },
                                    )
                                    Screen.Progress -> ProgressScreen()
                                    Screen.Settings -> SettingsScreen(
                                        lang = lang,
                                        onLang = {
                                            lang = it
                                            prefs.edit().putString("lang_code", it.code).apply()
                                        },
                                        onPaywall = {
                                            paywallFrom = Screen.Settings
                                            screen = Screen.Paywall
                                        },
                                    )
                                    Screen.Paywall -> PaywallScreen(onBack = { screen = paywallFrom })
                                    Screen.Regions -> RegionsScreen(
                                        onBack = { screen = Screen.Plan },
                                        onPaywall = {
                                            paywallFrom = Screen.Regions
                                            screen = Screen.Paywall
                                        },
                                    )
                                    is Screen.Detail -> DetailScreen(
                                        activityId = s.id,
                                        onBack = { screen = detailFrom },
                                    )
                                }
                                }
                            }
                            TabBar(
                                current = screen,
                                recording = recState.phase == Phase.RECORDING,
                                onSelect = { screen = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBar(current: Screen, recording: Boolean, onSelect: (Screen) -> Unit) {
    val t = LocalTexts.current
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = Palette.accent,
        selectedTextColor = Palette.accent,
        unselectedIconColor = Palette.textSecondary,
        unselectedTextColor = Palette.textSecondary,
        indicatorColor = Palette.accent.copy(alpha = 0.16f),
    )
    NavigationBar(containerColor = Palette.surface) {
        NavigationBarItem(
            selected = current is Screen.Record,
            onClick = { onSelect(Screen.Record) },
            icon = { RecordTabIcon(active = recording) },
            label = { TabLabel(t.tabRecord) },
            colors = colors,
        )
        NavigationBarItem(
            selected = current is Screen.Plan || current is Screen.Regions,
            onClick = { onSelect(Screen.Plan) },
            icon = { Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = { TabLabel(t.tabExplore) },
            colors = colors,
        )
        NavigationBarItem(
            selected = current is Screen.History || current is Screen.Detail,
            onClick = { onSelect(Screen.History) },
            icon = { Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = { TabLabel(t.tabHistory) },
            colors = colors,
        )
        NavigationBarItem(
            selected = current is Screen.Progress,
            onClick = { onSelect(Screen.Progress) },
            icon = { ProgressTabIcon() },
            label = { TabLabel(t.tabProgress) },
            colors = colors,
        )
        NavigationBarItem(
            selected = current is Screen.Settings || current is Screen.Paywall,
            onClick = { onSelect(Screen.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = { TabLabel(t.tabSettings) },
            colors = colors,
        )
    }
}

@Composable
private fun TabLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RecordTabIcon(active: Boolean) {
    val color = LocalContentColor.current
    val transition = rememberInfiniteTransition(label = "record-dot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot-pulse",
    )
    Canvas(modifier = Modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            color = color,
            radius = r * 0.78f,
            style = Stroke(width = r * 0.16f),
        )
        val dotColor = if (active) Palette.danger.copy(alpha = 0.55f + 0.45f * pulse) else color
        val dotRadius = if (active) r * (0.34f + 0.10f * pulse) else r * 0.38f
        drawCircle(
            color = dotColor,
            radius = dotRadius,
        )
    }
}

private fun tabRank(screen: Screen): Int = when (screen) {
    Screen.Record -> 0
    Screen.Plan, Screen.Regions -> 1
    Screen.History, is Screen.Detail -> 2
    Screen.Progress -> 3
    Screen.Settings, Screen.Paywall -> 4
}

@Composable
private fun ProgressTabIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width * 0.18f
        val gap = size.width * 0.10f
        val heights = listOf(0.42f, 0.66f, 0.92f)
        var x = (size.width - (w * 3 + gap * 2)) / 2f
        for (h in heights) {
            val bh = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - bh),
                size = Size(w, bh),
                cornerRadius = CornerRadius(w * 0.45f),
            )
            x += w + gap
        }
    }
}
