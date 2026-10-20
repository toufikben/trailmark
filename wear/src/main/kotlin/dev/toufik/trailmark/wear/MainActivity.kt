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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

@Composable
private fun RecorderScreen() {
    val context = LocalContext.current
    val state by WearSession.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            WearRecordService.start(context, "RUN")
        } else {
            WearSession.update { it.copy(notes = "Location permission missing") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1113))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "TRAILMARK",
            color = Color(0xC6FF3D),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatDistance(state.distanceM),
            fontWeight = FontWeight.Bold,
        )
        Text(text = formatTime(state.elapsedNanos))
        Spacer(Modifier.height(6.dp))
        if (state.recording) {
            Text(text = "recording", color = Color(0xC6FF3D))
        } else if (state.fixing) {
            Text(text = "finding GPS")
        } else {
            Text(text = "ready")
        }
        state.notes?.let { Text(text = it) }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (state.recording) {
                    WearRecordService.stop(context)
                } else if (hasLocation(context)) {
                    WearRecordService.start(context, "RUN")
                } else {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            },
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = if (state.recording) Color(0xFFB42318) else Color(0xC6FF3D),
                contentColor = if (state.recording) Color.White else Color.Black,
            ),
            modifier = Modifier.size(width = 150.dp, height = 48.dp),
        ) {
            Text(text = if (state.recording) "STOP" else "START")
        }
    }
}

private fun hasLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun formatDistance(meters: Double): String {
    if (meters < 1000.0) return "${meters.toInt()} m"
    return String.format("%.2f km", meters / 1000.0)
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
