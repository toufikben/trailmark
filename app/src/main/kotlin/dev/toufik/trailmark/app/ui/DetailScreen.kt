package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.toufik.trailmark.analytics.ReplayTrack
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.WeatherNow
import dev.toufik.trailmark.app.backfillSegment
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.app.matchSegments
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.data.EffortRow
import dev.toufik.trailmark.data.MediaRow
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.portable.Gpx
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Detail(
    val row: ActivityRow,
    val points: List<StoredPoint>,
    val media: List<MediaRow>,
    val analysis: dev.toufik.trailmark.analytics.Analysis,
)

@Composable
fun DetailScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val langNow = LocalLang.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<Detail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var viewerMedia by remember { mutableStateOf<MediaRow?>(null) }
    var efforts by remember { mutableStateOf<List<EffortRow>>(emptyList()) }
    var effortBests by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var segmentCount by remember { mutableStateOf(0) }
    var showCreateSegment by remember { mutableStateOf(false) }
    val packPath = remember { dev.toufik.trailmark.app.maps.MapPacks.installedMapPack(context)?.absolutePath }
    val darkMap = remember {
        context.getSharedPreferences("trailmark", Context.MODE_PRIVATE).getBoolean("map_dark", true)
    }

    LaunchedEffect(activityId) {
        detail = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            val a = repo.activityById(activityId) ?: return@withContext null
            val pts = repo.loadPoints(activityId)
            Detail(
                row = a,
                points = pts,
                media = repo.listMedia(activityId),
                analysis = dev.toufik.trailmark.analytics.analyze(pts, a.elapsedNanos),
            )
        }
        loading = false
        val loaded = detail ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = AppGraph.repository(context)
                matchSegments(repo, activityId, loaded.points)
                val list = repo.effortsForActivity(activityId)
                val bests = list.map { it.segmentId }.distinct()
                    .mapNotNull { sid -> repo.bestEffortNanos(sid)?.let { sid to it } }
                    .toMap()
                Triple(list, bests, repo.listSegments().size)
            }.getOrNull()
        }?.let { seg ->
            efforts = seg.first
            effortBests = seg.second
            segmentCount = seg.third
        }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(t.back, color = Palette.accent) }
                Text(t.details, color = Palette.text, fontSize = 22.sp)
            }

            val d = detail
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator(color = Palette.accent) }

                d == null -> Text(t.loadFailed, color = Palette.textSecondary)

                else -> {
                    val pace = if (d.row.distanceM < 50) null
                    else (d.row.elapsedNanos / 1e9) / (d.row.distanceM / 1000)
                    val distAnim by animateFloatAsState(
                        targetValue = d.row.distanceM.toFloat(),
                        animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
                        label = "hero-distance",
                    )
                    Text(
                        formatDistance(distAnim.toDouble()),
                        color = Palette.accent,
                        fontSize = 48.sp,
                        modifier = Modifier.appearIn(0),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).appearIn(1),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${t.time} ${formatDuration(d.row.elapsedNanos)}", color = Palette.text, fontSize = 15.sp)
                        Text(
                            "${t.pace} ${pace?.let { formatPace(it) } ?: "--:--"}",
                            color = Palette.text,
                            fontSize = 15.sp,
                        )
                        Text("${t.points} ${d.row.fixCount}", color = Palette.text, fontSize = 15.sp)
                    }
                    val a = d.analysis
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).appearIn(2),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${t.elevation} ${a.elevationGainM.toInt()} ${t.metre}",
                            color = Palette.textSecondary,
                            fontSize = 14.sp,
                        )
                        Text(
                            "${t.maxSpeed} ${String.format(java.util.Locale.US, "%.1f", a.maxSpeedMps * 3.6)} ${t.kmh}",
                            color = Palette.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    val wTemp = d.row.weatherTempC
                    val wWind = d.row.weatherWindKmh
                    val wDeg = d.row.weatherWindDeg
                    val wCode = d.row.weatherCode
                    if (wTemp != null && wWind != null && wDeg != null && wCode != null) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            WeatherChip(t = t, w = WeatherNow(wTemp, wWind, wDeg.toInt(), wCode.toInt()))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            shareGpx(context, d.points, d.row.startWallMillis, d.row.id, t.share)
                        }) { Text(t.share, color = Palette.accent) }
                        TextButton(onClick = {
                            scope.launch {
                                val card = withContext(Dispatchers.IO) {
                                    renderStoryCard(
                                        distanceM = d.row.distanceM,
                                        elapsedNanos = d.row.elapsedNanos,
                                        elevationGainM = a.elevationGainM,
                                        maxSpeedMps = a.maxSpeedMps,
                                        fixCount = d.row.fixCount,
                                        startWallMillis = d.row.startWallMillis,
                                        points = d.points,
                                        t = t,
                                        lang = langNow,
                                        sport = d.row.sport,
                                    )
                                }
                                shareStoryCardImage(context, card, d.row.id, t.storyCard)
                            }
                        }) { Text(t.storyCard, color = Palette.accent) }
                        TextButton(onClick = { showCreateSegment = true }) {
                            Text(t.createSegment, color = Palette.accent)
                        }
                    }
                    if (efforts.isNotEmpty()) {
                        Surface(
                            color = Palette.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).appearIn(3),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(t.segments, color = Palette.text, fontSize = 14.sp)
                                efforts.forEach { e ->
                                    val best = effortBests[e.segmentId]
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        Text(e.segmentName, color = Palette.text, fontSize = 14.sp)
                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                            Text(
                                                "${t.yourTime} ${formatDuration(e.elapsedNanos)}",
                                                color = Palette.textSecondary,
                                                fontSize = 12.sp,
                                            )
                                            if (best != null) {
                                                Text(
                                                    "   ${t.bestTime} ${formatDuration(best)}",
                                                    color = Palette.accent,
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                        if (best != null && best == e.elapsedNanos) {
                                            Text(t.newRecord, color = Palette.accent, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val track = remember(d) { ReplayTrack(d.points) }
                    var playing by remember(d) { mutableStateOf(false) }
                    var speed by remember(d) { mutableStateOf(1) }
                    var replayNanos by remember(d) { mutableStateOf(0L) }
                    val sample = track.sampleAt(replayNanos)
                    val cursor = sample?.let {
                        StoredPoint(
                            it.lat,
                            it.lon,
                            it.nanos,
                            5.0,
                            WaypointType.NORMAL,
                            bearingDeg = it.bearingDeg,
                        )
                    }
                    LaunchedEffect(playing, speed, d.media) {
                        if (!playing) return@LaunchedEffect
                        var last = withFrameNanos { it }
                        while (playing) {
                            withFrameNanos { now ->
                                val dt = (now - last).coerceIn(0L, 250_000_000L)
                                last = now
                                val next = replayNanos + dt * speed
                                val hit = d.media.firstOrNull { m ->
                                    m.elapsedNanos > replayNanos && m.elapsedNanos <= next
                                }
                                if (hit != null) {
                                    replayNanos = hit.elapsedNanos.coerceAtMost(track.totalNanos)
                                    playing = false
                                    if (hit.kind == "PHOTO") viewerMedia = hit
                                } else {
                                    replayNanos = next.coerceAtMost(track.totalNanos)
                                    if (replayNanos >= track.totalNanos) playing = false
                                }
                            }
                        }
                    }
                    TrailmarkMap(
                        points = d.points,
                        modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 12.dp).appearIn(3),
                        packPath = packPath,
                        current = cursor,
                        sport = sportOf(d.row.sport),
                        mediaMarks = d.media.mapIndexed { i, m -> MediaMark(i, m.lat, m.lon, m.kind) },
                        onMediaTap = { i ->
                            val m = d.media.getOrNull(i) ?: return@TrailmarkMap
                            if (m.kind == "VIDEO") {
                                openVideoPlayer(context, m.filePath)
                            } else {
                                viewerMedia = m
                            }
                        },
                        dark = darkMap,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReplayButton(
                            playing = playing,
                            description = if (playing) t.pause else t.resume,
                            onClick = {
                                if (!playing && track.totalNanos > 0L && replayNanos >= track.totalNanos) {
                                    replayNanos = 0L
                                }
                                playing = !playing
                            },
                        )
                        val total = track.totalNanos
                        Slider(
                            value = if (total > 0L) (replayNanos.toDouble() / total).toFloat() else 0f,
                            onValueChange = { f ->
                                playing = false
                                replayNanos = (total * f).toLong()
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${formatDistance(sample?.distanceM ?: 0.0)} ${t.km}",
                                color = Palette.accent,
                                fontSize = 13.sp,
                            )
                            Text(
                                "${formatDuration(replayNanos)} / ${formatDuration(total)}",
                                color = Palette.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(1, 4, 16, 60).forEach { s ->
                            FilterChip(
                                selected = speed == s,
                                onClick = { speed = s },
                                label = { Text("\u00d7$s", fontSize = 12.sp) },
                            )
                        }
                    }
                    if (d.media.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            d.media.forEach { m ->
                                MediaThumb(m) {
                                    if (m.kind == "VIDEO") {
                                        openVideoPlayer(context, m.filePath)
                                    } else {
                                        viewerMedia = m
                                    }
                                }
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 6.dp),
                    ) {
                        items(a.splits.size) { i ->
                            val s = a.splits[i]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${s.kmIndex} ${t.km}", color = Palette.text, fontSize = 13.sp)
                                Text(formatPace(s.paceSecPerKm), color = Palette.accent, fontSize = 13.sp)
                                Text(
                                    formatDuration(s.elapsedNanos),
                                    color = Palette.textSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        viewerMedia?.let { m ->
            if (m.kind == "PHOTO") {
                PhotoViewer(media = m, description = t.photo) { viewerMedia = null }
            }
        }
        val dd = detail
        if (showCreateSegment && dd != null && dd.points.size > 1) {
            SegmentCreateDialog(
                points = dd.points,
                defaultName = "${t.segmentSingular} ${segmentCount + 1}",
                onDismiss = { showCreateSegment = false },
                onCreate = { name, startIdx, endIdx ->
                    showCreateSegment = false
                    val pts = dd.points
                    scope.launch {
                        val segId = withContext(Dispatchers.IO) {
                            val repo = AppGraph.repository(context)
                            repo.insertSegment(
                                name = name,
                                startLat = pts[startIdx].lat,
                                startLon = pts[startIdx].lon,
                                endLat = pts[endIdx].lat,
                                endLon = pts[endIdx].lon,
                            )
                        }
                        val res = withContext(Dispatchers.IO) {
                            val repo = AppGraph.repository(context)
                            matchSegments(repo, dd.row.id, pts)
                            val list = repo.effortsForActivity(dd.row.id)
                            val bests = list.map { it.segmentId }.distinct()
                                .mapNotNull { sid -> repo.bestEffortNanos(sid)?.let { sid to it } }
                                .toMap()
                            list to bests
                        }
                        efforts = res.first
                        effortBests = res.second
                        segmentCount += 1
                        AppGraph.appScope.launch {
                            runCatching {
                                backfillSegment(AppGraph.repository(context), segId)
                            }
                        }
                    }
                },
            )
        }
    }
}

private fun sportOf(name: String): Sport = Sport.entries.firstOrNull { it.name == name } ?: Sport.RUN

/** Cumulative distance along the route at each point index, in metres. */
private fun cumulativeMeters(points: List<StoredPoint>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        out[i] = out[i - 1] + haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
    }
    return out
}

/** Picks a segment start/end along this route with two sliders and names it. */
@Composable
private fun SegmentCreateDialog(
    points: List<StoredPoint>,
    defaultName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, startIdx: Int, endIdx: Int) -> Unit,
) {
    val t = LocalTexts.current
    var name by remember { mutableStateOf(defaultName) }
    var startF by remember { mutableStateOf(0f) }
    var endF by remember { mutableStateOf(1f) }
    val cum = remember(points) { cumulativeMeters(points) }
    val lastIndex = (points.size - 1).coerceAtLeast(0)
    val startIdx = (startF * lastIndex).roundToInt().coerceIn(0, lastIndex)
    val endIdx = (endF * lastIndex).roundToInt().coerceIn(0, lastIndex)
    val valid = endIdx > startIdx && name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text(t.createSegment, color = Palette.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t.segmentName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${t.segStart} ${formatDistance(cum[startIdx])}",
                    color = Palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Slider(value = startF, onValueChange = { startF = it })
                Text("${t.segEnd} ${formatDistance(cum[endIdx])}", color = Palette.textSecondary, fontSize = 13.sp)
                Slider(value = endF, onValueChange = { endF = it })
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onCreate(name.trim(), startIdx, endIdx) }) {
                Text(t.createSegment, color = if (valid) Palette.accent else Palette.textSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = Palette.textSecondary) }
        },
    )
}

/** One gallery cell: a downsampled photo or a play badge for a video. */
@Composable
private fun MediaThumb(m: MediaRow, onOpen: () -> Unit) {
    val thumb by produceState<android.graphics.Bitmap?>(null, m.filePath) {
        if (m.kind == "PHOTO") {
            value = withContext(Dispatchers.IO) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(m.filePath, opts)
            }
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.surface)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (m.kind == "PHOTO") {
            thumb?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Canvas(modifier = Modifier.size(20.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.20f, size.height * 0.12f)
                    lineTo(size.width * 0.86f, size.height * 0.50f)
                    lineTo(size.width * 0.20f, size.height * 0.88f)
                    close()
                }
                drawPath(path, Palette.accent)
            }
        }
    }
}

/** Play/pause toggle for replay: accent disc with a dark play triangle, or pause bars. */
@Composable
private fun ReplayButton(playing: Boolean, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Palette.surface.copy(alpha = 0.9f))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.align(Alignment.Center).size(22.dp)) {
            if (playing) {
                val w = size.width * 0.14f
                val h = size.height * 0.52f
                drawRoundRect(
                    color = Palette.text,
                    topLeft = Offset(size.width * 0.31f - w / 2f, (size.height - h) / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w * 0.4f),
                )
                drawRoundRect(
                    color = Palette.text,
                    topLeft = Offset(size.width * 0.69f - w / 2f, (size.height - h) / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w * 0.4f),
                )
            } else {
                drawCircle(color = Palette.accent, radius = size.minDimension / 2f * 0.98f)
                val path = Path().apply {
                    moveTo(size.width * 0.40f, size.height * 0.30f)
                    lineTo(size.width * 0.72f, size.height * 0.50f)
                    lineTo(size.width * 0.40f, size.height * 0.70f)
                    close()
                }
                drawPath(path, Color(0xFF0E1113))
            }
        }
    }
}

private fun shareGpx(
    context: Context,
    points: List<StoredPoint>,
    startWallMillis: Long,
    id: Long,
    chooserTitle: String,
) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "trailmark-$id.gpx")
    file.writeText(Gpx.write(points, startWallMillis))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
