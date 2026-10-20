package dev.toufik.trailmark.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.app.distanceUnit
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.portable.Fit
import dev.toufik.trailmark.portable.Gpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit) {
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
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: return@withContext
                importTrackBytes(context, bytes)
            }
            reload()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val repo = AppGraph.repository(context)
                val rows = repo.listActivities().filter { !it.isOpen }
                if (rows.isEmpty()) return@withContext
                val out = context.contentResolver.openOutputStream(uri) ?: return@withContext
                val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                java.util.zip.ZipOutputStream(out.buffered()).use { zip ->
                    rows.forEach { row ->
                        val points = repo.loadPoints(row.id)
                        if (points.size < 2) return@forEach
                        val name = "trailmark-${fmt.format(java.util.Date(row.startWallMillis))}-${row.id}.gpx"
                        zip.putNextEntry(java.util.zip.ZipEntry(name))
                        zip.write(Gpx.write(points, row.startWallMillis).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.history, color = Palette.text, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    exportLauncher.launch("trailmark-tracks.zip")
                }) { Text(t.exportAll, color = Palette.textSecondary) }
                TextButton(onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/gpx+xml",
                            "application/vnd.ant.fit",
                            "application/zip",
                            "application/octet-stream",
                            "*/*",
                        ),
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
                    itemsIndexed(list, key = { _, row -> row.id }) { i, row ->
                        HistoryRow(row, index = i) { onOpen(row.id) }
                        HorizontalDivider(color = Palette.textSecondary.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: ActivityRow, index: Int = 0, onClick: () -> Unit) {
    val t = LocalTexts.current
    val pace = if (row.distanceM < 50) null else (row.elapsedNanos / 1e9) / (row.distanceM / 1000)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .appearIn(index.coerceAtMost(8), from = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(SPORT_COLORS[row.sport] ?: OTHER_SPORT_COLOR)
                }
                Text(
                    sportLabel(t, row.sport),
                    color = Palette.text,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(formatDate(row.startWallMillis, LocalLang.current), color = Palette.textSecondary, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${formatDistance(row.distanceM)} ${distanceUnit(t)}", color = Palette.text, fontSize = 14.sp)
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

/**
 * Imports a track from raw bytes: GPX text, a FIT file, or a ZIP archive that
 * holds any number of either. Returns true when at least one activity landed.
 */
internal fun importTrackBytes(context: Context, bytes: ByteArray): Boolean {
    if (
        bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    ) {
        var any = false
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val name = entry.name.lowercase()
                if (!entry.isDirectory && (name.endsWith(".fit") || name.endsWith(".gpx"))) {
                    if (importTrackBytes(context, zin.readBytes())) any = true
                }
            }
        }
        return any
    }
    val isFit = bytes.size >= 12 &&
        bytes[8] == '.'.code.toByte() && bytes[9] == 'F'.code.toByte() &&
        bytes[10] == 'I'.code.toByte() && bytes[11] == 'T'.code.toByte()
    val imported = if (isFit) Fit.read(bytes) else Gpx.read(bytes.toString(Charsets.UTF_8))
    if (imported.size < 2) return false
    val t0 = imported.first().timeMillis ?: System.currentTimeMillis()
    var dist = 0.0
    val pts = ArrayList<StoredPoint>(imported.size)
    imported.forEachIndexed { i, p ->
        if (i > 0) {
            dist += haversineMeters(imported[i - 1].lat, imported[i - 1].lon, p.lat, p.lon)
        }
        val tn = ((p.timeMillis ?: t0) - t0) * 1_000_000L
        pts.add(StoredPoint(p.lat, p.lon, tn, 5.0, WaypointType.NORMAL, altitudeM = p.altitudeM))
    }
    val repo = AppGraph.repository(context)
    val id = repo.insertActivity("import-${System.currentTimeMillis()}", "RUN", t0)
    repo.appendPoints(id, pts)
    repo.finishActivity(id, pts.last().timeNanos, dist, pts.size.toLong(), 0, false)
    return true
}
