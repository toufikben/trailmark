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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.service.RecordingService
import dev.toufik.trailmark.coach.Coach
import dev.toufik.trailmark.coach.CoachEvent
import dev.toufik.trailmark.coach.Locale
import dev.toufik.trailmark.engine.GpsQuality
import dev.toufik.trailmark.recording.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

private fun qualityLabel(q: GpsQuality): String = when (q) {
    GpsQuality.NONE, GpsQuality.SEARCHING -> "جارٍ البحث عن الإشارة…"
    GpsQuality.OK -> "تسجيل نشِط"
    GpsQuality.DEGRADED -> "إشارة ضعيفة"
    GpsQuality.LOST -> "فقدت الإشارة"
}

@Composable
fun RecordingScreen(onHistory: () -> Unit, onRegions: () -> Unit) {
    val context = LocalContext.current
    val state by RecordingHolder.state.collectAsState()
    var coachLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val last = withContext(Dispatchers.IO) {
            AppGraph.repository(context).listActivities()
                .firstOrNull { !it.isOpen && it.distanceM > 0 }
        }
        coachLine = last?.let {
            Coach.message(
                CoachEvent.PreviousBest(
                    distanceM = it.distanceM,
                    seconds = it.elapsedNanos / 1_000_000_000L,
                    targetM = it.distanceM,
                ),
                Locale.AR,
            )
        }
    }

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onHistory) {
                    Text("سجل الأنشطة", color = Palette.accent)
                }
                TextButton(onClick = onRegions) {
                    Text("الخرائط", color = Palette.accent)
                }
            }
            Text(
                text = formatDistance(state.distanceM),
                color = Palette.accent,
                fontSize = 64.sp,
                textAlign = TextAlign.Center,
            )
            Text(text = formatDuration(state.elapsedNanos), color = Palette.text, fontSize = 22.sp)
            Text(text = "النقاط: ${state.fixCount}", color = Palette.textSecondary, fontSize = 14.sp)
            coachLine?.let {
                Text(text = it, color = Palette.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Text(text = qualityLabel(state.quality), color = Palette.textSecondary, fontSize = 13.sp)
            if (state.isIdle) {
                Text(text = "متوقف مؤقتاً (خمول)", color = Palette.accent, fontSize = 13.sp)
            }
            state.storageError?.let {
                Text(
                    text = "التسجيل مستمر لكن النقاط لا تُحفظ",
                    color = Palette.danger,
                    fontSize = 13.sp,
                )
            }

            Column(modifier = Modifier.weight(1f)) {}

            when (state.phase) {
                Phase.RECORDING -> Button(
                    onClick = { stopService(context) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.danger,
                        contentColor = Palette.text,
                    ),
                ) { Text("إيقاف") }

                Phase.STARTING -> Button(
                    onClick = { stopService(context) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.surface,
                        contentColor = Palette.text,
                    ),
                ) { Text("إلغاء") }

                Phase.STOPPED -> Button(
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
