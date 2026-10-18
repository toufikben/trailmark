package dev.toufik.trailmark.app.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.SportPicker
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.app.plan.PlannedRoute
import dev.toufik.trailmark.app.plan.RoutePlanner
import dev.toufik.trailmark.core.geo.ElevPoint
import dev.toufik.trailmark.core.geo.RouteChunk
import dev.toufik.trailmark.core.geo.elevationRangeM
import dev.toufik.trailmark.core.geo.maxAbsGradePercent
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.engine.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Trip planner: tap the map to set a destination, pick a sport, and get a
 * road-following route with per-sport time estimate, elevation stats and a
 * gradient-colored line (red steep, amber mild, gray flat, green downhill).
 */
@Composable
fun PlanScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    val darkMap = remember { prefs.getBoolean("map_dark", true) }
    val packPath = remember { MapPacks.installedMapPack(context)?.absolutePath }
    var sport by remember { mutableStateOf(Sport.RUN) }
    var dest by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var anchor by remember { mutableStateOf<StoredPoint?>(null) }
    var plan by remember { mutableStateOf<PlannedRoute?>(null) }
    var loading by remember { mutableStateOf(false) }

    val locateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(dest, sport, anchor) {
        val d = dest
        val a = anchor
        if (d == null || a == null) {
            plan = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        plan = withContext(Dispatchers.IO) {
            RoutePlanner.plan(a.lat, a.lon, d.first, d.second, sport)
        }
        loading = false
    }

    Surface(color = Palette.bg, modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                TrailmarkMap(
                    points = emptyList(),
                    packPath = packPath,
                    planChunks = plan?.chunks ?: emptyList(),
                    planDest = dest,
                    onMapClick = { lat, lon -> dest = lat to lon },
                    onUserFix = { sp -> if (dest == null) anchor = sp },
                    onNeedPermission = {
                        locateLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    },
                    dark = darkMap,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Palette.surface.copy(alpha = 0.85f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = t.back, tint = Palette.text)
                        }
                        Text(
                            text = t.plan,
                            color = Palette.text,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SportPicker(t = t, sport = sport, enabled = true, onPick = { sport = it })
                }
            }

            Surface(
                color = Palette.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    val d = dest
                    when {
                        d == null -> Text(
                            t.pickDestination,
                            color = Palette.textSecondary,
                            fontSize = 13.sp,
                        )
                        loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Palette.accent,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(t.calculating, color = Palette.textSecondary, fontSize = 13.sp)
                        }
                        plan != null -> {
                            val p = plan!!
                            val secs = RoutePlanner.estimateSeconds(p, sport).toLong()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                PlanStat(formatDistance(p.distanceM), t.km)
                                PlanStat(formatDuration(secs * 1_000_000_000L), t.estimatedTime, accent = true)
                                PlanStat(arrivalClock(secs), t.estimatedArrival)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                PlanStat("↑ ${p.ascentM.toInt()} ${t.metre}", t.elevation)
                                PlanStat("↓ ${p.descentM.toInt()} ${t.metre}", t.descent)
                                PlanStat("%.0f%%".format(maxAbsGradePercent(p.chunks)), t.maxGrade)
                            }
                            if (p.direct) {
                                Spacer(Modifier.height(10.dp))
                                Text(t.directEstimate, color = Palette.textSecondary, fontSize = 11.sp)
                            } else {
                                Spacer(Modifier.height(12.dp))
                                Text(t.elevationProfile, color = Palette.textSecondary, fontSize = 11.sp)
                                ElevationChart(
                                    points = p.points,
                                    chunks = p.chunks,
                                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 6.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = {
                                    dest = null
                                    plan = null
                                }) {
                                    Text(t.clear, color = Palette.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStat(value: String, label: String, accent: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (accent) Palette.accent else Palette.text, fontSize = 16.sp)
        Text(label, color = Palette.textSecondary, fontSize = 11.sp)
    }
}

/** Mini elevation profile; each segment uses the same grade color as the route line. */
@Composable
private fun ElevationChart(
    points: List<ElevPoint>,
    chunks: List<RouteChunk>,
    modifier: Modifier = Modifier,
) {
    val (lo, hi) = remember(points) { elevationRangeM(points) }
    val span = (hi - lo).coerceAtLeast(1.0)
    Canvas(modifier) {
        val n = points.size
        if (n < 2) return@Canvas
        val stepX = size.width / (n - 1)
        fun yOf(e: Double): Float =
            size.height - (((e - lo) / span) * size.height * 0.92f + size.height * 0.04).toFloat()
        val fill = Path()
        fill.moveTo(0f, yOf(points[0].eleM))
        for (i in 1 until n) fill.lineTo(i * stepX, yOf(points[i].eleM))
        fill.lineTo(size.width, size.height)
        fill.lineTo(0f, size.height)
        fill.close()
        drawPath(fill, Palette.accent.copy(alpha = 0.12f))
        var cursor = 0
        for (chunk in chunks) {
            val cN = chunk.points.size
            if (cursor + cN > n) break
            for (i in cursor until cursor + cN - 1) {
                drawLine(
                    color = gradeColor(chunk.gradePercent),
                    start = Offset(i * stepX, yOf(points[i].eleM)),
                    end = Offset((i + 1) * stepX, yOf(points[i + 1].eleM)),
                    strokeWidth = size.height * 0.055f + 2f,
                    cap = StrokeCap.Round,
                )
            }
            cursor += cN - 1
        }
    }
}

private fun arrivalClock(seconds: Long): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.SECOND, seconds.toInt())
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
