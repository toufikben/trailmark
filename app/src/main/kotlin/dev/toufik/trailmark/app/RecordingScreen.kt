package dev.toufik.trailmark.app

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
fun RecordingScreen() {
    val controller = remember { RecordingController() }
    val scope = rememberCoroutineScope()
    val s = controller.state

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatDistance(s.distanceM),
                color = Palette.accent,
                fontSize = 64.sp,
                textAlign = TextAlign.Center,
            )
            Text(text = formatDuration(s.elapsedNanos), color = Palette.text, fontSize = 22.sp)
            Text(text = "النقاط: ${s.fixCount}", color = Palette.textSecondary, fontSize = 14.sp)
            Column(modifier = Modifier.weight(1f)) {}

            if (s.running) {
                Button(
                    onClick = { controller.stop() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.danger,
                        contentColor = Palette.text,
                    ),
                ) { Text("إيقاف") }
            } else {
                Button(
                    onClick = { controller.start(scope) },
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
