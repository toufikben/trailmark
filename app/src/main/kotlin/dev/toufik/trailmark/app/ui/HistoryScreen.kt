package dev.toufik.trailmark.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.health.HealthSync
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.app.distanceUnit
import dev.toufik.trailmark.app.extraText
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.portable.Fit
import dev.toufik.trailmark.portable.Gpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * The log, grouped by month and searchable.
 *
 * A flat list of four loose numbers stops working past a few dozen outings, so
 * each row is a card with the route drawn small, and the header carries the
 * month's totals. Filtering happens in memory: the list is already loaded.
 */
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ActivityRow>?>(null) }
    var query by remember { mutableStateOf("") }
    var sportFilter by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val routes = remember { mutableStateMapOf<Long, List<Offset>>() }

    fun reload() {
        scope.launch {
            rows = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ids = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: return@withContext emptyList<Long>()
                importTrackBytes(context, bytes)
            }
            if (ids.isNotEmpty()) {
                withContext(Dispatchers.IO) { HealthSync.writeActivities(context, ids) }
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
                val finished = repo.listActivities().filter { !it.isOpen }
                if (finished.isEmpty()) return@withContext
                val out = context.contentResolver.openOutputStream(uri) ?: return@withContext
                val fmt = java.text.SimpleDateFormat("yyyyMMdd", Locale.US)
                java.util.zip.ZipOutputStream(out.buffered()).use { zip ->
                    finished.forEach { row ->
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
        rows = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
    }

    val visible = remember(rows, query, sportFilter) {
        val all = rows ?: return@remember emptyList()
        val q = query.trim()
        all.filter { row ->
            (sportFilter == null || row.sport == sportFilter) &&
                (q.isEmpty() ||
                    sportLabel(t, row.sport).contains(q, ignoreCase = true) ||
                    formatDate(row.startWallMillis, lang).contains(q, ignoreCase = true))
        }
    }

    // Route outlines are only needed for what is on screen; load them once each.
    LaunchedEffect(visible) {
        val missing = visible.map { it.id }.filter { it !in routes }.take(60)
        if (missing.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            missing.mapNotNull { id ->
                val pts = repo.loadPoints(id)
                if (pts.size < 2) null else id to outline(pts)
            }
        }
        loaded.forEach { (id, pts) -> routes[id] = pts }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.history, color = Palette.text, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                Box {
                    Text(
                        "⋮",
                        color = Palette.textSecondary,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { menuOpen = true }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(t.exportAll) },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("trailmark-tracks.zip")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(t.importGpx) },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(
                                    arrayOf(
                                        "application/gpx+xml",
                                        "application/vnd.ant.fit",
                                        "application/zip",
                                        "application/octet-stream",
                                        "*/*",
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            SearchField(
                value = query,
                placeholder = extraText(lang, "search"),
                onChange = { query = it },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sportFilter == null,
                    onClick = { sportFilter = null },
                    label = { Text(t.progressAllSports, fontSize = 12.sp) },
                )
                listOf("RUN", "WALK", "BIKE", "HIKE").forEach { s ->
                    FilterChip(
                        selected = sportFilter == s,
                        onClick = { sportFilter = if (sportFilter == s) null else s },
                        label = { Text(sportLabel(t, s), fontSize = 12.sp) },
                    )
                }
            }

            val list = rows
            when {
                list == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Palette.accent) }

                list.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.noActivities, color = Palette.text, fontSize = 18.sp)
                    Text(t.recordFirst, color = Palette.textSecondary, fontSize = 13.sp)
                }

                visible.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(extraText(lang, "noResults"), color = Palette.textSecondary, fontSize = 14.sp)
                }

                else -> {
                    val groups = remember(visible) { groupByMonth(visible) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            MonthSummary(t, visible)
                        }
                        groups.forEach { (month, entries) ->
                            item(key = "h-$month") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        formatMonthYear(month, lang),
                                        color = Palette.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${entries.size} · ${formatDistance(entries.sumOf { it.distanceM })} ${distanceUnit(t)}",
                                        color = Palette.textSecondary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            items(entries, key = { it.id }) { row ->
                                HistoryCard(
                                    row = row,
                                    outline = routes[row.id],
                                    onClick = { onOpen(row.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Palette.textSecondary.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(Palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Text(
                    "✕",
                    color = Palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onChange("") }
                        .padding(6.dp),
                )
            }
        }
    }
}

/** The filtered total, so the list opens with a sense of scale. */
@Composable
private fun MonthSummary(t: Texts, rows: List<ActivityRow>) {
    val totalM = rows.sumOf { it.distanceM }
    val totalNanos = rows.sumOf { it.elapsedNanos }
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryCell(formatDistance(totalM), distanceUnit(t))
            SummaryCell("${rows.size}", t.progressActivities)
            SummaryCell(formatDuration(totalNanos), t.time)
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Palette.text, fontSize = 16.sp)
        Text(label, color = Palette.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun HistoryCard(row: ActivityRow, outline: List<Offset>?, onClick: () -> Unit) {
    val t = LocalTexts.current
    val lang = LocalLang.current
    val pace = if (row.distanceM < 50) null else (row.elapsedNanos / 1e9) / (row.distanceM / 1000)
    val color = SPORT_COLORS[row.sport] ?: OTHER_SPORT_COLOR
    Surface(
        color = Palette.surface.copy(alpha = if (row.isOpen) 0.9f else 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.bg),
                contentAlignment = Alignment.Center,
            ) {
                if (outline != null && outline.size >= 2) {
                    Canvas(modifier = Modifier.size(44.dp)) {
                        val minX = outline.minOf { it.x }
                        val maxX = outline.maxOf { it.x }
                        val minY = outline.minOf { it.y }
                        val maxY = outline.maxOf { it.y }
                        val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0f } ?: 1f
                        val pad = size.width * 0.08f
                        fun at(p: Offset) = Offset(
                            pad + (p.x - minX) / span * (size.width - 2 * pad),
                            pad + (p.y - minY) / span * (size.height - 2 * pad),
                        )
                        val path = Path().apply {
                            val first = at(outline.first())
                            moveTo(first.x, first.y)
                            for (k in 1 until outline.size) {
                                val q = at(outline[k])
                                lineTo(q.x, q.y)
                            }
                        }
                        drawPath(path, color, style = Stroke(width = size.width * 0.07f))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sportLabel(t, row.sport),
                        color = Palette.text,
                        fontSize = 15.sp,
                    )
                    if (row.isOpen) {
                        Text(
                            " · ${t.active}",
                            color = Palette.accent,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatDate(row.startWallMillis, lang),
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        formatDistance(row.distanceM),
                        color = Palette.text,
                        fontSize = 18.sp,
                    )
                    Text(
                        " ${distanceUnit(t)}",
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatDuration(row.elapsedNanos),
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(
                        "  ${pace?.let { formatPace(it) } ?: "--:--"}",
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/** `yyyyMM` grouping key; the entries stay in the order the repository returned. */
private fun groupByMonth(rows: List<ActivityRow>): List<Pair<Long, List<ActivityRow>>> {
    val out = LinkedHashMap<Long, MutableList<ActivityRow>>()
    for (r in rows) {
        val c = Calendar.getInstance()
        c.timeInMillis = r.startWallMillis
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        out.getOrPut(c.timeInMillis) { ArrayList() }.add(r)
    }
    return out.map { (k, v) -> k to v.toList() }
}

internal fun formatMonthYear(millis: Long, lang: dev.toufik.trailmark.app.Lang): String =
    java.text.SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag(lang.code))
        .format(java.util.Date(millis))

/** Downsampled route, normalised later by the canvas: at most 40 points per row. */
private fun outline(points: List<StoredPoint>): List<Offset> {
    val usable = points.filter { it.type != WaypointType.REJECTED }
    if (usable.size < 2) return emptyList()
    val step = (usable.size / 40).coerceAtLeast(1)
    return usable.filterIndexed { i, _ -> i % step == 0 || i == usable.lastIndex }
        .map { Offset(it.lat.toFloat(), it.lon.toFloat()) }
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
internal fun importTrackBytes(context: Context, bytes: ByteArray): List<Long> {
    if (
        bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    ) {
        val ids = ArrayList<Long>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val name = entry.name.lowercase()
                if (!entry.isDirectory && (name.endsWith(".fit") || name.endsWith(".gpx"))) {
                    ids.addAll(importTrackBytes(context, zin.readBytes()))
                }
            }
        }
        return ids
    }
    val isFit = bytes.size >= 12 &&
        bytes[8] == '.'.code.toByte() && bytes[9] == 'F'.code.toByte() &&
        bytes[10] == 'I'.code.toByte() && bytes[11] == 'T'.code.toByte()
    val imported = if (isFit) Fit.read(bytes) else Gpx.read(bytes.toString(Charsets.UTF_8))
    if (imported.size < 2) return emptyList()
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
    return listOf(id)
}
