package dev.toufik.trailmark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.toufik.trailmark.app.ui.DetailScreen
import dev.toufik.trailmark.app.ui.HistoryScreen
import dev.toufik.trailmark.app.ui.RegionsScreen

sealed interface Screen {
    data object Record : Screen
    data object History : Screen
    data object Regions : Screen
    data class Detail(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {
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
                        BackHandler(enabled = screen != Screen.Record) {
                            screen = when (val s = screen) {
                                is Screen.Detail -> Screen.History
                                else -> Screen.Record
                            }
                        }
                        when (val s = screen) {
                            Screen.Record -> RecordingScreen(
                                onHistory = { screen = Screen.History },
                                onRegions = { screen = Screen.Regions },
                                lang = lang,
                                onLang = { lang = it },
                            )
                            Screen.History -> HistoryScreen(
                                onOpen = { screen = Screen.Detail(it) },
                                onBack = { screen = Screen.Record },
                            )
                            Screen.Regions -> RegionsScreen(onBack = { screen = Screen.Record })
                            is Screen.Detail -> DetailScreen(
                                activityId = s.id,
                                onBack = { screen = Screen.History },
                            )
                        }
                    }
                }
            }
        }
    }
}
