package dev.toufik.trailmark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.toufik.trailmark.app.ui.DetailScreen
import dev.toufik.trailmark.app.ui.HistoryScreen

sealed interface Screen {
    data object Record : Screen
    data object History : Screen
    data class Detail(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrailmarkTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Record) }
                BackHandler(enabled = screen != Screen.Record) {
                    screen = when (val s = screen) {
                        is Screen.Detail -> Screen.History
                        else -> Screen.Record
                    }
                }
                when (val s = screen) {
                    Screen.Record -> RecordingScreen(onHistory = { screen = Screen.History })
                    Screen.History -> HistoryScreen(
                        onOpen = { screen = Screen.Detail(it) },
                        onBack = { screen = Screen.Record },
                    )
                    is Screen.Detail -> DetailScreen(
                        activityId = s.id,
                        onBack = { screen = Screen.History },
                    )
                }
            }
        }
    }
}
