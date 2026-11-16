package dev.toufik.trailmark.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import android.widget.Toast
import dev.toufik.trailmark.app.maps.RouteTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.ui.DetailScreen
import dev.toufik.trailmark.app.ui.HistoryScreen
import dev.toufik.trailmark.app.ui.Motion
import dev.toufik.trailmark.app.ui.PaywallScreen
import dev.toufik.trailmark.app.ui.PlanScreen
import dev.toufik.trailmark.app.ui.ProgressScreen
import dev.toufik.trailmark.app.ui.RegionsScreen
import dev.toufik.trailmark.app.ui.SettingsScreen
import dev.toufik.trailmark.app.ui.SplashScreen
import dev.toufik.trailmark.recording.Phase
import kotlinx.coroutines.delay

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

/** Stable text key per screen, so the open screen survives activity recreation. */
internal fun screenKey(screen: Screen): String = when (screen) {
    Screen.Record -> "record"
    Screen.Plan -> "plan"
    Screen.History -> "history"
    Screen.Progress -> "progress"
    Screen.Settings -> "settings"
    Screen.Regions -> "regions"
    Screen.Paywall -> "paywall"
    is Screen.Detail -> "detail:${screen.id}"
}

internal fun screenFromKey(key: String): Screen = when {
    key == "plan" -> Screen.Plan
    key == "history" -> Screen.History
    key == "progress" -> Screen.Progress
    key == "settings" -> Screen.Settings
    key == "regions" -> Screen.Regions
    key == "paywall" -> Screen.Paywall
    key.startsWith("detail:") -> key.removePrefix("detail:").toLongOrNull()
        ?.let { Screen.Detail(it) } ?: Screen.History
    else -> Screen.Record
}

private val ScreenSaver = Saver<Screen, String>(
    save = { screenKey(it) },
    restore = { screenFromKey(it) },
)

class MainActivity : ComponentActivity() {
    private var syntheticStarted = false
    private var benchStarted = false
    private var splashDone = false

    /** Kept on the activity so [onDestroy] can drop the store connection. */
    private var billing: BillingManager? = null

    override fun onDestroy() {
        billing?.dispose()
        billing = null
        super.onDestroy()
    }

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
        val benchId = intent?.getLongExtra("bench_flyover", -1L) ?: -1L
        if (debuggable && !benchStarted && benchId > 0) {
            benchStarted = true
            dev.toufik.trailmark.app.debug.FlyoverBench.run(this, lifecycleScope, benchId)
        }
    }

    /** Screen to switch to from outside Compose (an imported route opens Explore). */
    private val pendingScreen = mutableStateOf<Screen?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importSharedRoute(intent)
        openRequestedScreen(intent)
    }

    /** A notification ("clip ready") can ask for a screen by name. */
    private fun openRequestedScreen(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_SCREEN) ?: return
        intent.removeExtra(EXTRA_SCREEN)
        pendingScreen.value = screenFromKey(key)
    }

    /** A GPX opened or shared from Komoot, Strava or Files becomes a saved route. */
    private fun importSharedRoute(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        val code = getSharedPreferences("trailmark", Context.MODE_PRIVATE).getString("lang_code", null)
        val t = textsFor(Lang.entries.firstOrNull { it.code == code } ?: Lang.AR)
        lifecycleScope.launch {
            val route = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)
                    ?.let { RouteTransfer.readAtMost(it) }
                    ?.let { RouteTransfer.importGpx(this@MainActivity, it, t.importRoute) }
            }
            Toast.makeText(
                this@MainActivity,
                if (route != null) t.routeImported else t.routeImportFailed,
                Toast.LENGTH_LONG,
            ).show()
            if (route != null) pendingScreen.value = Screen.Plan
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.maplibre.android.MapLibre.getInstance(this)
        val prefs = getSharedPreferences("trailmark", Context.MODE_PRIVATE)
        dev.toufik.trailmark.app.Units.init(this)
        lifecycleScope.launch(Dispatchers.IO) {
            dev.toufik.trailmark.app.maps.SavedRoutes.preload(this@MainActivity)
        }
        val entitlements = Entitlements(this)
        val manager = BillingManager(this, entitlements)
        billing = manager
        manager.connect()
        // A recreated activity already handled the GPX it was opened with.
        if (savedInstanceState == null) {
            importSharedRoute(intent)
            openRequestedScreen(intent)
        }
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
                LocalBilling provides manager,
            ) {
                TrailmarkTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        var screen by rememberSaveable(stateSaver = ScreenSaver) {
                            mutableStateOf<Screen>(Screen.Record)
                        }
                        var paywallFrom by rememberSaveable(stateSaver = ScreenSaver) {
                            mutableStateOf<Screen>(Screen.Settings)
                        }
                        var detailFrom by rememberSaveable(stateSaver = ScreenSaver) {
                            mutableStateOf<Screen>(Screen.History)
                        }
                        var showSplash by remember { mutableStateOf(!splashDone) }
                        val recState by RecordingHolder.state.collectAsState()
                        var cameraOpen by remember { mutableStateOf(false) }
                        // While a run is being recorded the bar slims down to icons so the
                        // sheet below has room; it stays visible, there is nothing to reveal.
                        val compactNav = screen is Screen.Record &&
                            recState.phase == Phase.RECORDING && !cameraOpen
                        LaunchedEffect(screen) {
                            if (screen !is Screen.Record) cameraOpen = false
                        }
                        val pending = pendingScreen.value
                        LaunchedEffect(pending) {
                            if (pending != null) {
                                screen = pending
                                pendingScreen.value = null
                            }
                        }
                        LaunchedEffect(recState.phase, recState.lastActivityId) {
                            val id = recState.lastActivityId
                            if (recState.phase == Phase.STOPPED && id != null) {
                                ReviewPrompt.onRecordingStopped(this@MainActivity, id)
                                delay(2500)
                                ReviewPrompt.maybeAsk(this@MainActivity)
                            }
                        }
                        BackHandler(enabled = screen != Screen.Record) {
                            screen = when (val s = screen) {
                                is Screen.Detail -> detailFrom
                                Screen.Regions -> Screen.Plan
                                Screen.Paywall -> paywallFrom
                                else -> Screen.Record
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                        onPaywall = {
                                            paywallFrom = Screen.Record
                                            screen = Screen.Paywall
                                        },
                                        onCameraVisible = { cameraOpen = it },
                                    )
                                    Screen.Plan -> PlanScreen(
                                        onRegions = { screen = Screen.Regions },
                                        onStartRoute = { screen = Screen.Record },
                                    )
                                    Screen.History -> HistoryScreen(
                                        onOpen = { id ->
                                            detailFrom = Screen.History
                                            screen = Screen.Detail(id)
                                        },
                                    )
                                    Screen.Progress -> ProgressScreen(
                                        onPaywall = {
                                            paywallFrom = Screen.Progress
                                            screen = Screen.Paywall
                                        },
                                        onOpenActivity = { id ->
                                            detailFrom = Screen.Progress
                                            screen = Screen.Detail(id)
                                        },
                                    )
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
                                        onPaywall = {
                                            paywallFrom = s
                                            screen = Screen.Paywall
                                        },
                                        onStartRoute = { screen = Screen.Record },
                                    )
                                }
                                }
                            }
                            AnimatedVisibility(
                                visible = !cameraOpen,
                                enter = slideInVertically(tween(Motion.MED, easing = Motion.EaseOut)) { it },
                                exit = slideOutVertically(tween(Motion.MED, easing = Motion.EaseOut)) { it },
                            ) {
                                when {
                                    compactNav -> CompactTabBar(
                                        current = screen,
                                        recording = true,
                                        onSelect = { screen = it },
                                    )
                                    else -> TabBar(
                                        current = screen,
                                        recording = recState.phase == Phase.RECORDING,
                                        onSelect = { screen = it },
                                    )
                                }
                            }
                        }
                        if (showSplash) {
                            SplashScreen(
                                onDone = {
                                    splashDone = true
                                    showSplash = false
                                },
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Intent extra: a screen to open by key (a notification tapping into a clip). */
        const val EXTRA_SCREEN = "screen"
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

/** Icon-only tab bar shown while a run is being recorded: same destinations, less height. */
@Composable
private fun CompactTabBar(current: Screen, recording: Boolean, onSelect: (Screen) -> Unit) {
    val t = LocalTexts.current
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = Palette.accent,
        unselectedIconColor = Palette.textSecondary,
        indicatorColor = Palette.accent.copy(alpha = 0.16f),
    )
    // The inset lives on the wrapper: a padded bar keeps its icons above the system bar,
    // while a zero-inset NavigationBar would be drawn behind it where touches never land.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        NavigationBar(
            containerColor = Palette.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            NavigationBarItem(
                selected = current is Screen.Record,
                onClick = { onSelect(Screen.Record) },
                icon = { RecordTabIcon(active = recording) },
                label = null,
                alwaysShowLabel = false,
                colors = colors,
            )
            NavigationBarItem(
                selected = current is Screen.Plan || current is Screen.Regions,
                onClick = { onSelect(Screen.Plan) },
                icon = { Icon(Icons.Filled.Place, contentDescription = t.tabExplore, modifier = Modifier.size(22.dp)) },
                label = null,
                alwaysShowLabel = false,
                colors = colors,
            )
            NavigationBarItem(
                selected = current is Screen.History || current is Screen.Detail,
                onClick = { onSelect(Screen.History) },
                icon = { Icon(Icons.Filled.List, contentDescription = t.tabHistory, modifier = Modifier.size(22.dp)) },
                label = null,
                alwaysShowLabel = false,
                colors = colors,
            )
            NavigationBarItem(
                selected = current is Screen.Progress,
                onClick = { onSelect(Screen.Progress) },
                icon = { ProgressTabIcon() },
                label = null,
                alwaysShowLabel = false,
                colors = colors,
            )
            NavigationBarItem(
                selected = current is Screen.Settings || current is Screen.Paywall,
                onClick = { onSelect(Screen.Settings) },
                icon = { Icon(Icons.Filled.Settings, contentDescription = t.tabSettings, modifier = Modifier.size(22.dp)) },
                label = null,
                alwaysShowLabel = false,
                colors = colors,
            )
        }
    }
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
