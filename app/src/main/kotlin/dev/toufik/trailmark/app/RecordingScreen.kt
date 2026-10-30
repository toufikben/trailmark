package dev.toufik.trailmark.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.analytics.Ghost
import dev.toufik.trailmark.analytics.GhostCandidate
import dev.toufik.trailmark.analytics.GhostMatch
import dev.toufik.trailmark.analytics.ghostDeltaM
import dev.toufik.trailmark.analytics.matchGhost
import dev.toufik.trailmark.analytics.routeOverlapM
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.app.service.PlanRouteHolder
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.service.RecordingService
import dev.toufik.trailmark.app.service.RouteNavHolder
import dev.toufik.trailmark.app.service.WeatherHolder
import dev.toufik.trailmark.app.ui.MediaMark
import dev.toufik.trailmark.app.ui.Motion
import dev.toufik.trailmark.app.ui.PhotoViewer
import dev.toufik.trailmark.app.ui.TrailmarkMap
import dev.toufik.trailmark.app.ui.WeatherChip
import dev.toufik.trailmark.app.ui.openVideoPlayer
import dev.toufik.trailmark.app.ui.rememberPro
import dev.toufik.trailmark.coach.Coach
import dev.toufik.trailmark.coach.CoachEvent
import dev.toufik.trailmark.coach.Locale
import dev.toufik.trailmark.core.geo.TurnKind
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.MediaRow
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

private fun coachLocale(lang: Lang): Locale = when (lang) {
    Lang.AR -> Locale.AR
    Lang.FR -> Locale.FR
    else -> Locale.EN
}

@Composable
fun RecordingScreen(
    onOpenDetail: (Long) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    val darkMap = remember { prefs.getBoolean("map_dark", true) }
    val t = LocalTexts.current
    val langRef = rememberUpdatedState(LocalLang.current)
    val pro = rememberPro()
    val state by RecordingHolder.state.collectAsState()
    val packPath = remember(state.currentLat, state.currentLon) {
        MapPacks.packFor(context, state.currentLat, state.currentLon)?.absolutePath
    }
    val scope = rememberCoroutineScope()
    var sport by remember { mutableStateOf(Sport.RUN) }

    var pendingCapture by remember { mutableStateOf<java.io.File?>(null) }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var mediaFlash by remember { mutableStateOf(false) }

    var route by remember { mutableStateOf<List<StoredPoint>>(emptyList()) }
    var media by remember { mutableStateOf<List<MediaRow>>(emptyList()) }
    var viewerMedia by remember { mutableStateOf<MediaRow?>(null) }
    var coachLine by remember { mutableStateOf<String?>(null) }
    val weather by WeatherHolder.state.collectAsState()
    val planRoute by PlanRouteHolder.state.collectAsState()
    val nav by RouteNavHolder.state.collectAsState()
    var ghost by remember { mutableStateOf<Ghost?>(null) }
    var ghostRoute by remember { mutableStateOf<List<StoredPoint>>(emptyList()) }
    var ghostCandidates by remember { mutableStateOf<List<GhostCandidate>?>(null) }
    var ghostMatch by remember { mutableStateOf<GhostMatch?>(null) }
    var ghostValidated by remember { mutableStateOf(false) }
    var mapFix by remember { mutableStateOf<StoredPoint?>(null) }
    var barHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(state.activityId) {
        val id = state.activityId
        if (id == null) {
            route = emptyList()
            media = emptyList()
            return@LaunchedEffect
        }
        route = emptyList()
        var cursor = 0L
        while (true) {
            val repo = AppGraph.repository(context)
            val (chunk, mediaRows) = withContext(Dispatchers.IO) {
                repo.pointsAfter(id, cursor) to repo.listMedia(id)
            }
            if (chunk.isNotEmpty()) {
                route = route + chunk.map { it.second }
                cursor = chunk.last().first
            }
            media = mediaRows
            delay(1500)
        }
    }

    LaunchedEffect(state.lastActivityId) {
        ghostCandidates = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            repo.listActivities()
                .filter { !it.isOpen && it.distanceM >= 40.0 }
                .sortedByDescending { it.startWallMillis }
                .take(30)
                .mapNotNull { row ->
                    val points = repo.loadPoints(row.id).filter { it.type != WaypointType.REJECTED }
                    if (points.size < 2) {
                        null
                    } else {
                        GhostCandidate(
                            activityId = row.id,
                            sport = row.sport,
                            startWallMillis = row.startWallMillis,
                            distanceM = row.distanceM,
                            elapsedNanos = row.elapsedNanos,
                            points = points,
                        )
                    }
                }
        }
    }

    // Pick the ghost once the first real fix lands, using the same sport, the
    // start point, the heading and (a little later) the actual route overlap.
    val stateRef = rememberUpdatedState(state)
    val routeRef = rememberUpdatedState(route)
    val mapFixRef = rememberUpdatedState(mapFix)
    LaunchedEffect(state.activityId, sport, ghostCandidates, pro) {
        val id = state.activityId
        if (id == null) {
            ghost = null
            ghostRoute = emptyList()
            ghostMatch = null
            ghostValidated = false
            return@LaunchedEffect
        }
        if (!pro) {
            // Ghost racing is a Pro feature: free users record without a rival.
            ghost = null
            ghostRoute = emptyList()
            ghostMatch = null
            ghostValidated = false
            return@LaunchedEffect
        }
        if (ghostMatch != null) return@LaunchedEffect
        val candidates = ghostCandidates ?: return@LaunchedEffect
        if (candidates.isEmpty()) return@LaunchedEffect

        var start: StoredPoint? = null
        var startBearing: Double? = null
        var waited = 0L
        while (start == null && waited < 15_000L) {
            val s = stateRef.value
            val fix = mapFixRef.value
            val la = s.currentLat ?: fix?.lat
            val lo = s.currentLon ?: fix?.lon
            if (la != null && lo != null) {
                start = StoredPoint(la, lo, 0L, 5.0, WaypointType.NORMAL)
                startBearing = s.currentBearing ?: fix?.bearingDeg
            } else {
                delay(200L)
                waited += 200L
            }
        }
        val from = start ?: return@LaunchedEffect
        val m = matchGhost(
            sport = sport.name,
            startLat = from.lat,
            startLon = from.lon,
            currentBearing = startBearing,
            currentRoute = routeRef.value,
            candidates = candidates,
        ) ?: return@LaunchedEffect

        val points = m.candidate.points.filter { it.type != WaypointType.REJECTED }
        if (points.size < 2) return@LaunchedEffect
        ghostMatch = m
        ghost = Ghost(points, points.first().timeNanos)
        ghostRoute = points
        coachLine = Coach.message(
            CoachEvent.PreviousBest(
                distanceM = m.candidate.distanceM,
                seconds = m.candidate.elapsedNanos / 1_000_000_000L,
                targetM = m.candidate.distanceM,
            ),
            coachLocale(langRef.value),
        )
    }

    // A heading match at the start is only a guess. Once ~4 fixes exist, drop
    // the ghost if the two routes do not actually share the same ground.
    LaunchedEffect(state.activityId, route.size) {
        val m = ghostMatch ?: return@LaunchedEffect
        if (ghostValidated || route.size < 4) return@LaunchedEffect
        val overlap = routeOverlapM(route, m.candidate.points) ?: return@LaunchedEffect
        ghostValidated = true
        if (overlap > 45.0) {
            ghost = null
            ghostRoute = emptyList()
            ghostMatch = null
        }
    }

    val moveBearing = remember(route) { movementBearing(route) }
    val current: StoredPoint? = state.currentLat?.let { la ->
        state.currentLon?.let { lo ->
            StoredPoint(
                la,
                lo,
                state.elapsedNanos,
                5.0,
                WaypointType.NORMAL,
                bearingDeg = state.currentBearing ?: moveBearing,
            )
        }
    }

    val ghostNow: StoredPoint? = ghost?.positionAt(state.elapsedNanos)

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
                Coach.message(CoachEvent.Overtook(Math.abs(d)), coachLocale(langRef.value), seed)
            } else {
                Coach.message(CoachEvent.GotOvertaken(Math.abs(d)), coachLocale(langRef.value), seed)
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

    fun onCaptured(ok: Boolean) {
        val file = pendingCapture
        val kind = pendingKind
        pendingCapture = null
        pendingKind = null
        val activityId = state.activityId
        if (!ok || file == null || kind == null || activityId == null) return
        val lat = state.currentLat
        val lon = state.currentLon
        val bearing = state.currentBearing
        val distanceM = state.distanceM
        val elapsedNanos = state.elapsedNanos
        scope.launch {
            withContext(Dispatchers.IO) {
                AppGraph.repository(context).insertMedia(
                    activityId = activityId,
                    kind = kind,
                    filePath = file.absolutePath,
                    lat = lat,
                    lon = lon,
                    bearingDeg = bearing,
                    distanceM = distanceM,
                    elapsedNanos = elapsedNanos,
                    createdWallMillis = System.currentTimeMillis(),
                    durationMs = if (kind == "VIDEO") MediaCapture.videoDurationMs(file) else null,
                    caption = null,
                )
            }
            mediaFlash = true
            delay(2500)
            mediaFlash = false
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> onCaptured(ok) }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { ok -> onCaptured(ok) }

    fun capturePhoto() {
        val file = MediaCapture.newPhotoFile(context)
        pendingCapture = file
        pendingKind = "PHOTO"
        try {
            photoLauncher.launch(MediaCapture.uriFor(context, file))
        } catch (_: Exception) {
            pendingCapture = null
            pendingKind = null
        }
    }

    fun captureVideo() {
        val file = MediaCapture.newVideoFile(context)
        pendingCapture = file
        pendingKind = "VIDEO"
        try {
            videoLauncher.launch(MediaCapture.uriFor(context, file))
        } catch (_: Exception) {
            pendingCapture = null
            pendingKind = null
        }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                    speedMps = state.currentSpeedMps,
                    planChunks = planRoute,
                    ghostRoute = if (state.phase == Phase.RECORDING || state.phase == Phase.PAUSED) {
                        ghostRoute
                    } else {
                        emptyList()
                    },
                    ghostCurrent = if (state.phase == Phase.RECORDING || state.phase == Phase.PAUSED) {
                        ghostNow
                    } else {
                        null
                    },
                    mediaMarks = media.mapIndexed { i, m -> MediaMark(i, m.lat, m.lon, m.kind) },
                    onMediaTap = { i ->
                        val m = media.getOrNull(i) ?: return@TrailmarkMap
                        if (m.kind == "VIDEO") {
                            openVideoPlayer(context, m.filePath)
                        } else {
                            viewerMedia = m
                        }
                    },
                    onNeedPermission = {
                        locateLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    },
                    onUserFix = { mapFix = it },
                    dark = darkMap,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = qualityLabel(t, state.quality),
                        color = Palette.text,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    val w = weather
                    if (w != null && state.phase != Phase.STOPPED) {
                        WeatherChip(t = t, w = w)
                    }
                    if (planRoute.isNotEmpty()) {
                        Text(
                            text = "${t.routeLabel} ✕",
                            color = Palette.accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.surface.copy(alpha = 0.85f))
                                .clickable { PlanRouteHolder.clear() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    val n = nav
                    if (n != null && state.phase != Phase.STOPPED) {
                        val arrow = when (n.nextTurn) {
                            TurnKind.LEFT,
                            TurnKind.SLIGHT_LEFT,
                            TurnKind.SHARP_LEFT -> "↰"
                            TurnKind.RIGHT,
                            TurnKind.SLIGHT_RIGHT,
                            TurnKind.SHARP_RIGHT -> "↱"
                            TurnKind.UTURN -> "↶"
                            null -> null
                        }
                        val label = when {
                            n.offRoute -> "⚠"
                            arrow != null -> if (n.turnInM >= 1000.0) {
                                "$arrow ${formatDistance(n.turnInM)} ${distanceUnit(t)}"
                            } else {
                                "$arrow ${formatElevation(n.turnInM)} ${elevationUnit(t)}"
                            }
                            else -> "${formatDistance(n.remainingM)} ${distanceUnit(t)}"
                        }
                        Text(
                            text = label,
                            color = if (n.offRoute) Color(0xFFE5484D) else Palette.accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.surface.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                if (state.isIdle || state.phase == Phase.PAUSED) {
                    Text(
                        text = when {
                            state.autoPaused -> t.autoPause
                            state.phase == Phase.PAUSED -> t.paused
                            else -> t.pause
                        },
                        color = Palette.accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (ghostMatch != null && liveDelta != null && state.phase == Phase.RECORDING) {
                    val d = liveDelta
                    val ahead = d >= 0
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text("👻", fontSize = 13.sp)
                        Text(t.ghost, color = Palette.textSecondary, fontSize = 11.sp)
                        Text(
                            (if (ahead) "+" else "−") +
                                formatElevation(Math.abs(d).toDouble()) + " " + elevationUnit(t),
                            color = if (ahead) Palette.accent else Color(0xFFFFB224),
                            fontSize = 14.sp,
                        )
                    }
                }
                if (mediaFlash) {
                    Text(
                        text = t.mediaSaved,
                        color = Palette.accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 52.dp)
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
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                val elapsedAnim by animateFloatAsState(
                    targetValue = (state.elapsedNanos / 1e9).toFloat(),
                    animationSpec = tween(Motion.MED, easing = Motion.EaseOut),
                    label = "elapsed",
                )
                val distAnim by animateFloatAsState(
                    targetValue = state.distanceM.toFloat(),
                    animationSpec = tween(Motion.MED, easing = Motion.EaseOut),
                    label = "distance",
                )
                val speedAnim by animateFloatAsState(
                    targetValue = (state.currentSpeedMps ?: 0.0).toFloat(),
                    animationSpec = tween(500, easing = Motion.EaseOut),
                    label = "speed",
                )
                val paceText = if (distAnim < 50) "--:--"
                else formatPace(elapsedAnim.toDouble() / (distAnim / 1000.0))
                val speedText = if (state.currentSpeedMps == null) "--"
                else formatSpeed(speedAnim.toDouble())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    ) {
                        StatCell(formatDuration((elapsedAnim * 1e9).toLong()), t.time)
                        StatCell(
                            formatDistance(distAnim.toDouble()),
                            distanceUnit(t),
                            accent = true,
                            desc = "${formatDistance(distAnim.toDouble())} ${distanceUnit(t)}",
                        )
                    }
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
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    ) {
                        StatCell(speedText, t.speed)
                        StatCell(paceText, t.pace)
                    }
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
                if (overtakeFlash != null) {
                    Text(text = overtakeFlash!!, color = Palette.accent, fontSize = 12.sp)
                }
                state.storageError?.let {
                    Text(text = t.loadFailed, color = Palette.dangerText, fontSize = 12.sp)
                }

                val lastId = state.lastActivityId
                if (state.phase == Phase.STOPPED && lastId != null) {
                    TextButton(onClick = { onOpenDetail(lastId) }) {
                        Text(t.details, color = Palette.accent)
                    }
                }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(LocalDensity.current) { barHeightPx.toDp() } + 12.dp),
            ) {
                if (state.phase == Phase.PAUSED && state.activityId != null) {
                    MediaCaptureRow(
                        t = t,
                        onPhoto = { capturePhoto() },
                        onVideo = { captureVideo() },
                    )
                } else {
                    SportPicker(
                        t = t,
                        sport = sport,
                        enabled = state.phase == Phase.STOPPED,
                        onPick = { sport = it },
                    )
                }
            }

            viewerMedia?.let { m ->
                if (m.kind == "PHOTO") {
                    BackHandler { viewerMedia = null }
                    PhotoViewer(
                        media = m,
                        description = t.photo,
                        captionLabel = t.caption,
                        onSaveCaption = { cap ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppGraph.repository(context).setMediaCaption(m.id, cap)
                                }
                                viewerMedia = m.copy(caption = cap)
                            }
                        },
                        onDismiss = { viewerMedia = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, accent: Boolean = false, desc: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (desc != null) {
            Modifier.clearAndSetSemantics { contentDescription = desc }
        } else {
            Modifier
        },
    ) {
        Text(
            text = value,
            color = if (accent) Palette.accent else Palette.text,
            fontSize = 16.sp,
        )
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
    val active = phase == Phase.RECORDING || phase == Phase.PAUSED
    val pulseTransition = rememberInfiniteTransition(label = "record-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier = modifier.size(56.dp)) {
        if (active) {
            Canvas(modifier = Modifier.matchParentSize().scale(1.5f)) {
                drawCircle(
                    color = Palette.danger.copy(alpha = 0.10f + 0.16f * pulse),
                    radius = size.minDimension / 2f,
                )
            }
        }
        Box(
        modifier = Modifier
            .fillMaxSize()
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
}

/** Bearing of the last meaningful movement, used when GPS does not report one. */
private fun movementBearing(route: List<StoredPoint>): Double? {
    val last = route.lastOrNull() ?: return null
    val prev = route.asReversed().drop(1).firstOrNull {
        haversineMeters(it.lat, it.lon, last.lat, last.lon) >= 6.0
    } ?: return null
    return bearingDeg(prev.lat, prev.lon, last.lat, last.lon)
}

private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lon2 - lon1)
    val y = Math.sin(dl) * Math.cos(p2)
    val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

/** Photo and video buttons shown while the recording is paused. */
@Composable
private fun MediaCaptureRow(
    t: Texts,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        CaptureRoundButton(description = t.photo, onClick = onPhoto) { color ->
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.10f, h * 0.32f),
                size = Size(w * 0.80f, h * 0.46f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = Stroke(width = w * 0.09f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.30f, h * 0.20f),
                size = Size(w * 0.22f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.04f),
                style = Stroke(width = w * 0.09f),
            )
            drawCircle(
                color = color,
                radius = w * 0.13f,
                center = Offset(w * 0.50f, h * 0.55f),
                style = Stroke(width = w * 0.09f),
            )
        }
        CaptureRoundButton(description = t.video, onClick = onVideo) { color ->
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.10f, h * 0.30f),
                size = Size(w * 0.62f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = Stroke(width = w * 0.09f),
            )
            val path = Path().apply {
                moveTo(w * 0.76f, h * 0.44f)
                lineTo(w * 0.92f, h * 0.34f)
                lineTo(w * 0.92f, h * 0.74f)
                lineTo(w * 0.76f, h * 0.64f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun CaptureRoundButton(
    description: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Palette.surface.copy(alpha = 0.94f),
        modifier = Modifier
            .size(52.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
        ) {
            icon(Palette.text)
        }
    }
}

