package dev.toufik.trailmark.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.analytics.Ghost
import dev.toufik.trailmark.analytics.ghostDeltaM
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.service.RecordingService
import dev.toufik.trailmark.app.ui.TrailmarkMap
import dev.toufik.trailmark.coach.Coach
import dev.toufik.trailmark.coach.CoachEvent
import dev.toufik.trailmark.coach.Locale
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.GpsQuality
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.recording.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun startService(context: Context, sport: Sport) {
    val intent = Intent(context, RecordingService::class.java)
        .setAction(RecordingService.ACTION_START)
        .putExtra(RecordingService.EXTRA_SPORT, sport.name)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopService(context: Context) {
    context.startService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
    )
}

private fun pauseService(context: Context) {
    context.startService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_PAUSE),
    )
}

private fun resumeService(context: Context) {
    context.startService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_RESUME),
    )
}

private fun qualityLabel(t: Texts, q: GpsQuality): String = when (q) {
    GpsQuality.NONE, GpsQuality.SEARCHING -> t.searching
    GpsQuality.OK -> t.active
    GpsQuality.DEGRADED -> t.weak
    GpsQuality.LOST -> t.lost
}

@Composable
fun RecordingScreen(
    onHistory: () -> Unit,
    onRegions: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    lang: Lang,
    onLang: (Lang) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    var darkMap by remember { mutableStateOf(prefs.getBoolean("map_dark", true)) }
    val t = LocalTexts.current
    val state by RecordingHolder.state.collectAsState()
    val packPath = remember { MapPacks.installedMapPack(context)?.absolutePath }
    var sport by remember { mutableStateOf(Sport.RUN) }

    var route by remember { mutableStateOf<List<StoredPoint>>(emptyList()) }
    var coachLine by remember { mutableStateOf<String?>(null) }
    var ghost by remember { mutableStateOf<Ghost?>(null) }
    var ghostRoute by remember { mutableStateOf<List<StoredPoint>>(emptyList()) }
    var ghostStartNanos by remember { mutableStateOf(0L) }
    var barHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(state.activityId) {
        val id = state.activityId
        if (id == null) {
            route = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            route = withContext(Dispatchers.IO) { AppGraph.repository(context).loadPoints(id) }
            delay(1500)
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            val last = repo.listActivities().firstOrNull { !it.isOpen && it.distanceM > 0 }
                ?: return@withContext null
            last to repo.loadPoints(last.id)
        }
        if (loaded != null) {
            val (row, points) = loaded
            coachLine = Coach.message(
                CoachEvent.PreviousBest(
                    distanceM = row.distanceM,
                    seconds = row.elapsedNanos / 1_000_000_000L,
                    targetM = row.distanceM,
                ),
                Locale.AR,
            )
            if (points.size >= 2) {
                ghost = Ghost(points, points.first().timeNanos)
                ghostRoute = points
                ghostStartNanos = points.first().timeNanos
            }
        }
    }

    val current: StoredPoint? = state.currentLat?.let { la ->
        state.currentLon?.let { lo ->
            StoredPoint(la, lo, state.elapsedNanos, 5.0, WaypointType.NORMAL, bearingDeg = state.currentBearing)
        }
    }

    val ghostNow: StoredPoint? = if (ghostRoute.size < 2) {
        null
    } else {
        val idx = ghostRoute.indexOfLast { it.timeNanos - ghostStartNanos <= state.elapsedNanos }
        if (idx < 0) {
            null
        } else {
            val p = ghostRoute[idx]
            val prev = ghostRoute.getOrNull(idx - 1)
            val b = if (prev != null) bearingDeg(prev.lat, prev.lon, p.lat, p.lon) else p.bearingDeg
            p.copy(bearingDeg = b ?: p.bearingDeg)
        }
    }

    val gLive = ghost
    val liveDelta: Int? = if (gLive != null && state.phase == Phase.RECORDING) {
        ghostDeltaM(gLive, state.distanceM, state.elapsedNanos).toInt()
    } else {
        null
    }
    var lastAhead by remember { mutableStateOf<Boolean?>(null) }
    var overtakeFlash by remember { mutableStateOf<String?>(null) }
    val liveDeltaRef = rememberUpdatedState(liveDelta)
    LaunchedEffect(liveDelta?.let { it >= 0 }, state.phase) {
        val d = liveDeltaRef.value
        if (d == null) {
            lastAhead = null
            overtakeFlash = null
            return@LaunchedEffect
        }
        val ahead = d >= 0
        val prev = lastAhead
        lastAhead = ahead
        if (prev != null && prev != ahead) {
            val seed = (state.elapsedNanos / 60_000_000_000L).toInt()
            overtakeFlash = if (ahead) {
                Coach.message(CoachEvent.Overtook(Math.abs(d)), Locale.AR, seed)
            } else {
                Coach.message(CoachEvent.GotOvertaken(Math.abs(d)), Locale.AR, seed)
            }
            delay(5000)
            overtakeFlash = null
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
    ) { result -> if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) startService(context, sport) }

    val locateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                TrailmarkMap(
                    points = route,
                    packPath = packPath,
                    current = current,
                    attributionBottom = barHeightPx,
                    sport = sport,
                    ghostRoute = ghostRoute,
                    ghostCurrent = ghostNow,
                    onNeedPermission = {
                        locateLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    },
                    dark = darkMap,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Palette.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Lang.entries.forEach { l ->
                            TextButton(
                                onClick = { onLang(l) },
                                modifier = Modifier.heightIn(min = 44.dp),
                            ) {
                                Text(
                                    l.code.uppercase(),
                                    color = if (l == lang) Palette.accent else Palette.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Palette.surface.copy(alpha = 0.85f)),
                    ) {
                        IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.List, contentDescription = t.history, tint = Palette.text)
                        }
                        IconButton(onClick = onRegions, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Place, contentDescription = t.regions, tint = Palette.text)
                        }
                        IconButton(
                            onClick = {
                                darkMap = !darkMap
                                prefs.edit().putBoolean("map_dark", darkMap).apply()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Canvas(Modifier.size(22.dp).semantics { contentDescription = t.mapTheme }) {
                                val tint = Palette.text
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val r = size.minDimension * 0.20f
                                if (darkMap) {
                                    for (i in 0 until 8) {
                                        rotate(i * 45f) {
                                            drawLine(
                                                color = tint,
                                                start = Offset(cx, cy - r * 1.6f),
                                                end = Offset(cx, cy - r * 2.35f),
                                                strokeWidth = r * 0.52f,
                                                cap = StrokeCap.Round,
                                            )
                                        }
                                    }
                                    drawCircle(color = tint, radius = r, center = Offset(cx, cy))
                                } else {
                                    val o = size.minDimension * 0.42f
                                    val path = Path().apply {
                                        arcTo(Rect(cx - o * 1.1f, cy - o, cx + o * 1.1f, cy + o), 90f, 180f, true)
                                        arcTo(Rect(cx - o * 1.35f, cy - o, cx + o * 0.65f, cy + o), 270f, 180f, false)
                                        close()
                                    }
                                    drawPath(path, tint)
                                }
                            }
                        }
                    }
                }
                Text(
                    text = qualityLabel(t, state.quality),
                    color = Palette.text,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                if (state.isIdle || state.phase == Phase.PAUSED) {
                    Text(
                        text = if (state.phase == Phase.PAUSED) t.pause else t.paused,
                        color = Palette.accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Surface(
                color = Palette.surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { barHeightPx = it.height },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                if (state.phase == Phase.STOPPED) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Sport.entries.forEach { s ->
                            FilterChip(
                                selected = sport == s,
                                enabled = state.phase == Phase.STOPPED,
                                onClick = { if (state.phase == Phase.STOPPED) sport = s },
                                label = { Text(sportLabelOf(t, s)) },
                            )
                        }
                    }
                }
                val paceText = if (state.distanceM < 50) "--:--"
                else formatPace((state.elapsedNanos / 1e9) / (state.distanceM / 1000))
                val speedText = state.currentSpeedMps?.let { "%.1f".format(it * 3.6) } ?: "--"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDistance(state.distanceM),
                            color = Palette.accent,
                            fontSize = 30.sp,
                            modifier = Modifier.semantics {
                                contentDescription = "${formatDistance(state.distanceM)} ${t.km}"
                            },
                        )
                        Text(text = t.km, color = Palette.textSecondary, fontSize = 11.sp)
                    }
                    StatCell(formatDuration(state.elapsedNanos), t.time)
                    StatCell(paceText, t.pace)
                    StatCell(speedText, t.speed)
                    val startAction = {
                        if (granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                            startService(context, sport)
                        } else {
                            launcher.launch(permissions)
                        }
                    }
                    RoundRecordButton(
                        phase = state.phase,
                        description = when (state.phase) {
                            Phase.STOPPED -> t.start
                            Phase.RECORDING -> "${t.pause} \u00b7 ${t.holdToFinish}"
                            Phase.PAUSED -> "${t.resume} \u00b7 ${t.holdToFinish}"
                            Phase.STARTING -> t.stop
                        },
                        onClick = {
                            when (state.phase) {
                                Phase.STOPPED -> startAction()
                                Phase.RECORDING -> pauseService(context)
                                Phase.PAUSED -> resumeService(context)
                                Phase.STARTING -> stopService(context)
                            }
                        },
                        onLongClick = {
                            if (state.phase == Phase.STOPPED) startAction() else stopService(context)
                        },
                    )
                }
                if (state.phase == Phase.RECORDING || state.phase == Phase.PAUSED) {
                    Text(
                        t.holdToFinish,
                        color = Palette.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                coachLine?.let {
                    Text(text = it, color = Palette.textSecondary, fontSize = 12.sp)
                }
                if (ghost != null && state.phase == Phase.RECORDING && liveDelta != null) {
                    val seed = (state.elapsedNanos / 20_000_000_000L).toInt()
                    val line = overtakeFlash ?: if (liveDelta >= 0) {
                        Coach.message(CoachEvent.GhostBehind(Math.abs(liveDelta)), Locale.AR, seed)
                    } else {
                        Coach.message(CoachEvent.GhostAhead(Math.abs(liveDelta)), Locale.AR, seed)
                    }
                    Text(text = line, color = Palette.accent, fontSize = 12.sp)
                }
                state.storageError?.let {
                    Text(text = t.loadFailed, color = Palette.danger, fontSize = 12.sp)
                }

                val lastId = state.lastActivityId
                if (state.phase == Phase.STOPPED && lastId != null) {
                    TextButton(onClick = { onOpenDetail(lastId) }) {
                        Text(t.details, color = Palette.accent)
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Palette.text, fontSize = 16.sp)
        Text(text = label, color = Palette.textSecondary, fontSize = 11.sp)
    }
}

private const val HOLD_TO_FINISH_NANOS = 2_000_000_000L

/** Tap starts/pauses/resumes; a 2-second hold finishes (the ring shows the countdown). */
@Composable
private fun RoundRecordButton(
    phase: Phase,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when (phase) {
        Phase.RECORDING -> Palette.danger
        Phase.PAUSED -> Palette.danger
        Phase.STARTING -> Palette.surface
        Phase.STOPPED -> Palette.accent
    }
    val iconTint = if (phase == Phase.STARTING) Palette.text else Color.White
    var holdFrac by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(phase) {
                awaitEachGesture {
                    awaitFirstDown()
                    var fired = false
                    val t0 = System.nanoTime()
                    val holdJob = scope.launch {
                        while (true) {
                            val f = (System.nanoTime() - t0).toDouble() / HOLD_TO_FINISH_NANOS
                            holdFrac = f.toFloat().coerceIn(0f, 1f)
                            if (f >= 1.0) {
                                fired = true
                                onLongClick()
                                break
                            }
                            delay(16L)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    if (up != null && !fired) onClick()
                    holdFrac = 0f
                }
            }
            .semantics {
                contentDescription = description
                this.onClick(label = description) {
                    onClick()
                    true
                }
                this.onLongClick(label = description) {
                    onLongClick()
                    true
                }
            },
    ) {
        if (holdFrac > 0f && (phase == Phase.RECORDING || phase == Phase.PAUSED)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.07f
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * holdFrac,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp),
        ) {
            when (phase) {
                Phase.STOPPED -> drawCircle(color = iconTint, radius = size.minDimension / 2f)
                Phase.RECORDING -> {
                    val w = size.width * 0.14f
                    val h = size.height * 0.52f
                    drawRoundRect(
                        color = iconTint,
                        topLeft = Offset(size.width * 0.31f - w / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w * 0.4f),
                    )
                    drawRoundRect(
                        color = iconTint,
                        topLeft = Offset(size.width * 0.69f - w / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w * 0.4f),
                    )
                }
                Phase.PAUSED -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.40f, size.height * 0.30f)
                        lineTo(size.width * 0.72f, size.height * 0.50f)
                        lineTo(size.width * 0.40f, size.height * 0.70f)
                        close()
                    }
                    drawPath(path, iconTint)
                }
                Phase.STARTING -> {
                    val side = size.minDimension * 0.82f
                    drawRoundRect(
                        color = iconTint,
                        topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(side * 0.22f),
                    )
                }
            }
        }
    }
}

private fun sportLabelOf(t: Texts, s: Sport): String = when (s) {
    Sport.WALK -> t.walk
    Sport.RUN -> t.run
    Sport.BIKE -> t.bike
    Sport.HIKE -> t.hike
}

private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lon2 - lon1)
    val y = Math.sin(dl) * Math.cos(p2)
    val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

