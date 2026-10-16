package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.data.ActivityRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Detail(val row: ActivityRow, val points: List<StoredPoint>)

@Composable
fun DetailScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<Detail?>(null) }
    var loading by remember { mutableStateOf(true) }
    val packPath = remember { dev.toufik.trailmark.app.maps.MapPacks.installedMapPack(context)?.absolutePath }

    LaunchedEffect(activityId) {
        detail = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            val a = repo.activityById(activityId) ?: return@withContext null
            Detail(a, repo.loadPoints(activityId))
        }
        loading = false
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("رجوع ›", color = Palette.accent) }
                Text("تفاصيل النشاط", color = Palette.text, fontSize = 22.sp)
            }

            val d = detail
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator(color = Palette.accent) }

                d == null -> Text("تعذّر تحميل النشاط", color = Palette.textSecondary)

                else -> {
                    val pace = if (d.row.distanceM < 50) null
                    else (d.row.elapsedNanos / 1e9) / (d.row.distanceM / 1000)
                    Text(
                        formatDistance(d.row.distanceM),
                        color = Palette.accent,
                        fontSize = 48.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("الوقت ${formatDuration(d.row.elapsedNanos)}", color = Palette.text, fontSize = 15.sp)
                        Text(
                            "الإيقاع ${pace?.let { formatPace(it) } ?: "--:--"}",
                            color = Palette.text,
                            fontSize = 15.sp,
                        )
                        Text("النقاط ${d.row.fixCount}", color = Palette.text, fontSize = 15.sp)
                    }
                    TrailmarkMap(
                        points = d.points,
                        modifier = Modifier.fillMaxWidth().height(360.dp).padding(top = 12.dp),
                        mbtilesPath = packPath,
                    )
                }
            }
        }
    }
}
