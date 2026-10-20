package dev.toufik.trailmark.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RecorderScreen() } }
    }
}

private const val ACCENT = 0xFFC6FF3D.toInt()
private const val DANGER = 0xFFB42318.toInt()
private const val BG = 0xFF0E1113.toInt()
private const val TEXT = 0xFFF4F7F2.toInt()
private const val TEXT_SECONDARY = 0xFF9DA4A1.toInt()

private val SPORTS = listOf("RUN", "WALK", "BIKE", "HIKE")

@Composable
private fun RecorderScreen() {
    val context = LocalContext.current
    val state by WearSession.state.collectAsState()
    var sport by remember { mutableStateOf(SPORTS.first()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            WearRecordService.start(context, sport)
        } else {
            WearSession.update { it.copy(notes = "Location permission missing") }
        }
    }

    fun begin() {
        if (hasLocation(context)) {
            WearRecordService.start(context, sport)
        } else {
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(BG))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "TRAILMARK",
            color = Color(ACCENT),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatDistance(state.distanceM),
            color = Color(TEXT),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatTime(state.elapsedNanos),
            color = Color(TEXT_SECONDARY),
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = sportLabel(sport),
            color = if (state.recording) Color(ACCENT) else Color(TEXT),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(enabled = !state.recording) {
                val next = (SPORTS.indexOf(sport) + 1) % SPORTS.size
                sport = SPORTS[next]
            },
        )
        Text(
            text = when {
                state.recording -> "recording"
                state.fixing -> "finding GPS"
                else -> "tap sport to change"
            },
            color = if (state.recording) Color(ACCENT) else Color(TEXT_SECONDARY),
            fontSize = 11.sp,
        )
        state.notes?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                color = Color(TEXT_SECONDARY),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (state.recording) WearRecordService.stop(context) else begin()
            },
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = if (state.recording) Color(DANGER) else Color(ACCENT),
                contentColor = if (state.recording) Color.White else Color.Black,
            ),
            modifier = Modifier.size(width = 140.dp, height = 52.dp),
        ) {
            Text(
                text = if (state.recording) "STOP" else "START",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun hasLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun sportLabel(sport: String): String = when (sport) {
    "WALK" -> "WALK"
    "BIKE" -> "RIDE"
    "HIKE" -> "HIKE"
    else -> "RUN"
}

private fun formatDistance(meters: Double): String {
    if (meters < 1000.0) return "${meters.toInt()} m"
    return String.format(Locale.US, "%.2f km", meters / 1000.0)
}

private fun formatTime(nanos: Long): String {
    val total = nanos / 1_000_000_000L
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
