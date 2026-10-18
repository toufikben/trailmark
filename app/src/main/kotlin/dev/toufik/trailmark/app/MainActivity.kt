package dev.toufik.trailmark.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
    data class Detail(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {
    private var syntheticStarted = false

    override fun onResume() {
        super.onResume()
        if (!syntheticStarted && intent?.getBooleanExtra("synthetic", false) == true) {
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
        setContent {
            var lang by remember { mutableStateOf(Lang.AR) }
            val texts = textsFor(lang)
            val direction = if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalTexts provides texts, LocalLang provides lang) {
                TrailmarkTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        var screen by remember { mutableStateOf<Screen>(Screen.Record) }
                        val recState by RecordingHolder.state.collectAsState()
                        BackHandler(enabled = screen != Screen.Record) {
                            screen = when (val s = screen) {
                                is Screen.Detail -> Screen.History
                                Screen.Regions -> Screen.Plan
                                else -> Screen.Record
                            }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (val s = screen) {
                                    Screen.Record -> RecordingScreen(
                                        onOpenDetail = { id -> screen = Screen.Detail(id) },
                                    )
                                    Screen.Plan -> PlanScreen(
                                        onRegions = { screen = Screen.Regions },
                                    )
                                    Screen.History -> HistoryScreen(
                                        onOpen = { screen = Screen.Detail(it) },
                                    )
                                    Screen.Progress -> ProgressScreen()
                                    Screen.Settings -> SettingsScreen(lang = lang, onLang = { lang = it })
                                    Screen.Regions -> RegionsScreen(onBack = { screen = Screen.Plan })
                                    is Screen.Detail -> DetailScreen(
                                        activityId = s.id,
                                        onBack = { screen = Screen.History },
                                    )
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
        indicatorColor = Palette.bg,
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
            icon = { Icon(Icons.Filled.Place, contentDescription = t.tabExplore, modifier = Modifier.size(22.dp)) },
            label = { TabLabel(t.tabExplore) },
            colors = colors,
        )
        NavigationBarItem(
            selected = current is Screen.History || current is Screen.Detail,
            onClick = { onSelect(Screen.History) },
            icon = { Icon(Icons.Filled.List, contentDescription = t.tabHistory, modifier = Modifier.size(22.dp)) },
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
            selected = current is Screen.Settings,
            onClick = { onSelect(Screen.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = t.tabSettings, modifier = Modifier.size(22.dp)) },
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
    Canvas(modifier = Modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            color = color,
            radius = r * 0.78f,
            style = Stroke(width = r * 0.16f),
        )
        drawCircle(
            color = if (active) Palette.danger else color,
            radius = r * 0.38f,
        )
    }
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
