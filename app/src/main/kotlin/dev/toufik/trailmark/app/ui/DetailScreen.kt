package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.toufik.trailmark.analytics.ReplayTrack
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.portable.Gpx
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Detail(val row: ActivityRow, val points: List<StoredPoint>)

@Composable
fun DetailScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
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
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
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
                    Text(
                        formatDistance(d.row.distanceM),
                        color = Palette.accent,
                        fontSize = 48.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    val a = remember(d) {
                        dev.toufik.trailmark.analytics.analyze(d.points, d.row.elapsedNanos)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${t.elevation} ${a.elevationGainM.toInt()} ${t.metre}",
                            color = Palette.textSecondary,
                            fontSize = 14.sp,
                        )
                        Text(
                            "${t.maxSpeed} ${"%.1f".format(a.maxSpeedMps * 3.6)} ${t.kmh}",
                            color = Palette.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            shareGpx(context, d.points, d.row.startWallMillis, d.row.id)
                        }) { Text(t.share, color = Palette.accent) }
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
                    LaunchedEffect(playing, speed) {
                        if (!playing) return@LaunchedEffect
                        var last = withFrameNanos { it }
                        while (playing) {
                            withFrameNanos { now ->
                                val dt = (now - last).coerceIn(0L, 250_000_000L)
                                last = now
                                replayNanos = (replayNanos + dt * speed).coerceAtMost(track.totalNanos)
                                if (replayNanos >= track.totalNanos) playing = false
                            }
                        }
                    }
                    TrailmarkMap(
                        points = d.points,
                        modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 12.dp),
                        packPath = packPath,
                        current = cursor,
                        sport = sportOf(d.row.sport),
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
}

private fun sportOf(name: String): Sport = Sport.entries.firstOrNull { it.name == name } ?: Sport.RUN

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

private fun shareGpx(context: Context, points: List<StoredPoint>, startWallMillis: Long, id: Long) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "trailmark-$id.gpx")
    file.writeText(Gpx.write(points, startWallMillis))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "مشاركة GPX"))
}
