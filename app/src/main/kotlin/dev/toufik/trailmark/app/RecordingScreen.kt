package dev.toufik.trailmark.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.service.RecordingService
import dev.toufik.trailmark.recording.Phase

/** Metres of accumulated distance as `km,ss`. */
fun formatDistance(metres: Double): String {
    val km = metres / 1000.0
    val whole = km.toLong()
    val frac = Math.round((km - whole) * 100)
    val f = if (frac == 100L) 0L else frac
    val w = if (frac == 100L) whole + 1 else whole
    return "$w,${f.toString().padStart(2, '0')}"
}

/** Elapsed monotonic nanos as `mm:ss` (adding `hh:` past an hour). */
fun formatDuration(nanos: Long): String {
    val s = nanos / 1_000_000_000L
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun p(n: Long) = n.toString().padStart(2, '0')
    return if (h == 0L) "${p(m)}:${p(sec)}" else "${p(h)}:${p(m)}:${p(sec)}"
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun startService(context: Context) {
    val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopService(context: Context) {
    context.startService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
    )
}

@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    val state by RecordingHolder.state.collectAsState()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) startService(context) }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatDistance(state.distanceM),
                color = Palette.accent,
                fontSize = 64.sp,
                textAlign = TextAlign.Center,
            )
            Text(text = formatDuration(state.elapsedNanos), color = Palette.text, fontSize = 22.sp)
            Text(text = "النقاط: ${state.fixCount}", color = Palette.textSecondary, fontSize = 14.sp)
            state.storageError?.let {
                Text(
                    text = "التسجيل مستمر لكن النقاط لا تُحفظ",
                    color = Palette.danger,
                    fontSize = 13.sp,
                )
            }

            Column(modifier = Modifier.weight(1f)) {}

            if (state.phase == Phase.RECORDING) {
                Button(
                    onClick = { stopService(context) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.danger,
                        contentColor = Palette.text,
                    ),
                ) { Text("إيقاف") }
            } else {
                Button(
                    onClick = {
                        if (granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                            startService(context)
                        } else {
                            launcher.launch(permissions)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.surface,
                        contentColor = Palette.text,
                    ),
                ) { Text("ابدأ التسجيل") }
            }
        }
    }
}
