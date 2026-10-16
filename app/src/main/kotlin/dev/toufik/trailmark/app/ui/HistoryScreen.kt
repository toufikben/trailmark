package dev.toufik.trailmark.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.portable.Gpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ActivityRow>?>(null) }

    fun reload() {
        scope.launch {
            items = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@withContext
                val imported = Gpx.read(text)
                if (imported.size < 2) return@withContext
                val t0 = imported.first().timeMillis ?: System.currentTimeMillis()
                var dist = 0.0
                val pts = ArrayList<StoredPoint>(imported.size)
                imported.forEachIndexed { i, p ->
                    if (i > 0) {
                        dist += haversineMeters(
                            imported[i - 1].lat, imported[i - 1].lon, p.lat, p.lon,
                        )
                    }
                    val tn = ((p.timeMillis ?: t0) - t0) * 1_000_000L
                    pts.add(StoredPoint(p.lat, p.lon, tn, 5.0, WaypointType.NORMAL, altitudeM = p.altitudeM))
                }
                val repo = AppGraph.repository(context)
                val id = repo.insertActivity("import-${System.currentTimeMillis()}", "RUN", t0)
                repo.appendPoints(id, pts)
                repo.finishActivity(id, pts.last().timeNanos, dist, pts.size.toLong(), 0, false)
            }
            reload()
        }
    }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text(t.back, color = Palette.accent) }
                Text(t.history, color = Palette.text, fontSize = 22.sp)
                TextButton(onClick = {
                    importLauncher.launch(
                        arrayOf("application/gpx+xml", "application/octet-stream", "*/*"),
                    )
                }) { Text(t.importGpx, color = Palette.accent) }
            }

            val list = items
            when {
                list == null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator(color = Palette.accent) }

                list.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.noActivities, color = Palette.text, fontSize = 18.sp)
                    Text(
                        t.recordFirst,
                        color = Palette.textSecondary,
                        fontSize = 13.sp,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { row ->
                        HistoryRow(row) { onOpen(row.id) }
                        HorizontalDivider(color = Palette.surface)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: ActivityRow, onClick: () -> Unit) {
    val t = LocalTexts.current
    val pace = if (row.distanceM < 50) null else (row.elapsedNanos / 1e9) / (row.distanceM / 1000)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(sportLabel(t, row.sport), color = Palette.text, fontSize = 16.sp)
            Text(formatDate(row.startWallMillis), color = Palette.textSecondary, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${t.km} ${formatDistance(row.distanceM)}", color = Palette.text, fontSize = 14.sp)
            Text(formatDuration(row.elapsedNanos), color = Palette.text, fontSize = 14.sp)
            Text(pace?.let { formatPace(it) } ?: "--:--", color = Palette.text, fontSize = 14.sp)
            if (row.isOpen) Text(t.active, color = Palette.accent, fontSize = 13.sp)
        }
    }
}

private fun sportLabel(t: Texts, sport: String): String = when (sport) {
    "RUN" -> t.run
    "WALK" -> t.walk
    "BIKE" -> t.bike
    "HIKE" -> t.hike
    else -> sport
}
