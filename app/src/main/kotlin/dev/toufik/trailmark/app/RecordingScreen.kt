package dev.toufik.trailmark.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.toufik.trailmark.analytics.GhostCandidate
import dev.toufik.trailmark.analytics.GhostMatch
import dev.toufik.trailmark.analytics.MAX_GHOST_AGE_MILLIS
import dev.toufik.trailmark.analytics.MIN_GHOST_DISTANCE_M
import dev.toufik.trailmark.analytics.MIN_GHOST_DURATION_NANOS
import dev.toufik.trailmark.analytics.rankGhosts
import dev.toufik.trailmark.analytics.routeOverlapM
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.app.service.GhostSession
import dev.toufik.trailmark.app.service.PlanRouteHolder
import dev.toufik.trailmark.app.service.RecordingHolder
import dev.toufik.trailmark.app.service.RecordingService
import dev.toufik.trailmark.app.service.RouteNavHolder
import dev.toufik.trailmark.app.service.WeatherHolder
import dev.toufik.trailmark.app.ui.MediaMark
import dev.toufik.trailmark.app.ui.Motion
import dev.toufik.trailmark.app.ui.PhotoViewer
import dev.toufik.trailmark.app.ui.ProCta
import dev.toufik.trailmark.app.ui.SpeedometerScreen
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

/** Every UI language has its own coach bank now; the enum names line up. */
private fun coachLocale(lang: Lang): Locale =
    Locale.entries.firstOrNull { it.name == lang.name } ?: Locale.EN

@Composable
fun RecordingScreen(
    onOpenDetail: (Long) -> Unit,
    onPaywall: () -> Unit,
    onCameraVisible: (Boolean) -> Unit = {},
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

    // Saved across activity recreation: the camera app often outlives our activity,
    // and the result must still find the file it was told to write.
    var cameraMode by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaFlash by remember { mutableStateOf(false) }

    // The tab bar goes away while the viewfinder is open, so the preview gets the space.
    LaunchedEffect(cameraMode != null) { onCameraVisible(cameraMode != null) }

    var route by remember { mutableStateOf<List<StoredPoint>>(emptyList()) }
    var media by remember { mutableStateOf<List<MediaRow>>(emptyList()) }
    var viewerMedia by remember { mutableStateOf<MediaRow?>(null) }
    var coachLine by remember { mutableStateOf<String?>(null) }
    val weather by WeatherHolder.state.collectAsState()
    val planRoute by PlanRouteHolder.state.collectAsState()
    val nav by RouteNavHolder.state.collectAsState()
    // The race itself lives in the holder: this screen is destroyed on every tab
    // change and rotation, and rebuilt state restarted the clock and reported the
    // whole run as a lead.
    val ghostSession by RecordingHolder.session.collectAsState()
    val ghostPool by RecordingHolder.pool.collectAsState()
    val ghostNoMatch by RecordingHolder.noMatch.collectAsState()
    val holderDelta by RecordingHolder.ghostDelta.collectAsState()
    var ghostCandidates by remember { mutableStateOf<List<GhostCandidate>?>(null) }
    var mapFix by remember { mutableStateOf<StoredPoint?>(null) }
    var barHeightPx by remember { mutableStateOf(0) }
    var showSpeedometer by remember { mutableStateOf(false) }
    val lang = LocalLang.current

    LaunchedEffect(state.activityId) {
        val id = state.activityId
        if (id == null) {
            route = emptyList()
            media = emptyList()
            // The run is over: no rival can still be racing.
            RecordingHolder.clearGhost()
            return@LaunchedEffect
        }
        // A session that belongs to another effort is stale (a run finished while
        // this screen was gone); drop it, but never clear the live one.
        val live = RecordingHolder.session.value
        if (live != null && live.activityId != id) RecordingHolder.clearGhost()
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

    // Rivals are loaded once per finished run. Mocked fixes, very short efforts
    // and stale attempts are dropped here so the ranking never has to carry them.
    // The open row also restores the sport after a rotation, so a resumed bike
    // ride is not compared against runs. It is skipped once a race has joined,
    // because changing the sport then would undo the pick.
    LaunchedEffect(state.activityId) {
        val id = state.activityId ?: return@LaunchedEffect
        if (RecordingHolder.session.value?.activityId == id) return@LaunchedEffect
        val row = withContext(Dispatchers.IO) {
            AppGraph.repository(context).activityById(id)
        }
        val recorded = row?.sport?.let { name ->
            Sport.entries.firstOrNull { it.name == name }
        }
        if (recorded != null) sport = recorded
    }

    // Rivals are loaded when a run starts and again when one finishes. Keyed only
    // on the finished run, the very first run after the app launched had no pool
    // at all and silently raced nothing.
    LaunchedEffect(state.activityId, state.lastActivityId) {
        if (state.activityId == null && state.lastActivityId == null) {
            ghostCandidates = emptyList()
            return@LaunchedEffect
        }
        ghostCandidates = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            val now = System.currentTimeMillis()
            repo.listActivities()
                .filter {
                    !it.isOpen &&
                        !it.mockDetected &&
                        it.elapsedNanos >= MIN_GHOST_DURATION_NANOS &&
                        it.distanceM >= MIN_GHOST_DISTANCE_M &&
                        now - it.startWallMillis <= MAX_GHOST_AGE_MILLIS
                }
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
                            mockDetected = row.mockDetected,
                        )
                    }
                }
        }
    }

    // Pick the ghost once the first real fix lands, using the same sport, the
    // start point, the heading and (a little later) the actual route overlap.
    // The join is deliberately deferred while a live session for this activity
    // already exists, which is what makes a tab switch free.
    val stateRef = rememberUpdatedState(state)
    val routeRef = rememberUpdatedState(route)
    val mapFixRef = rememberUpdatedState(mapFix)
    val sessionRef = rememberUpdatedState(ghostSession)
    LaunchedEffect(state.activityId, sport, ghostCandidates, pro) {
        val id = state.activityId
        if (id == null) {
            RecordingHolder.clearGhost()
            return@LaunchedEffect
        }
        if (!pro) {
            // Ghost racing is a Pro feature: free users record without a rival.
            RecordingHolder.clearGhost()
            return@LaunchedEffect
        }
        if (sessionRef.value?.activityId == id) return@LaunchedEffect
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
        // Ranking walks every stored point of up to 30 efforts: keep it off the
        // UI thread, which also draws the map.
        val ranked = withContext(Dispatchers.Default) {
            rankGhosts(
                sport = sport.name,
                startLat = from.lat,
                startLon = from.lon,
                currentBearing = startBearing,
                currentRoute = routeRef.value,
                candidates = candidates,
                nowWallMillis = System.currentTimeMillis(),
            )
        }
        // Another instance may have joined while this one was waiting for a fix.
        if (sessionRef.value?.activityId == id) return@LaunchedEffect
        val best = ranked.firstOrNull()
        if (best == null) {
            // Rivals existed but none of them started near this run: say so
            // instead of leaving the runner wondering where the ghost went.
            RecordingHolder.setNoMatch(true)
            return@LaunchedEffect
        }
        RecordingHolder.setPool(ranked.drop(1))
        RecordingHolder.setNoMatch(false)
        RecordingHolder.setSession(
            sessionOf(best, id, stateRef.value.elapsedNanos, stateRef.value.distanceM),
        )
        coachLine = Coach.message(
            CoachEvent.PreviousBest(
                distanceM = best.remainingM,
                seconds = best.remainingNanos / 1_000_000_000L,
                targetM = best.remainingM,
            ),
            coachLocale(langRef.value),
        )
    }

    // A heading match at the start is only a guess. Once ~4 fixes exist, check
    // that the two routes share the same ground; if they do not, race the next
    // rival instead of silently dropping the ghost for the rest of the run.
    LaunchedEffect(state.activityId, route.size, ghostSession?.candidateId) {
        val s = ghostSession ?: return@LaunchedEffect
        if (s.activityId != state.activityId || s.validated || route.size < 4) return@LaunchedEffect
        val liveRoute = route
        val overlap = withContext(Dispatchers.Default) {
            routeOverlapM(liveRoute, s.match.candidate.points)
        } ?: return@LaunchedEffect
        if (overlap <= 45.0) {
            RecordingHolder.setSession(s.copy(validated = true))
            return@LaunchedEffect
        }
        val liveId = s.activityId
        val next = withContext(Dispatchers.Default) {
            ghostPool.firstOrNull { m ->
                (routeOverlapM(route, m.candidate.points) ?: Double.MAX_VALUE) <= 45.0
            }
        }
        if (next == null) {
            RecordingHolder.clearGhost()
            RecordingHolder.setNoMatch(true)
            return@LaunchedEffect
        }
        val st = stateRef.value
        RecordingHolder.setPool(ghostPool - next)
        RecordingHolder.setNoMatch(false)
        RecordingHolder.setSession(sessionOf(next, liveId, st.elapsedNanos, st.distanceM))
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

    val session = ghostSession?.takeIf { it.activityId == state.activityId }
    val ghostNow: StoredPoint? = session
        ?.takeIf { state.phase == Phase.RECORDING || state.phase == Phase.PAUSED }
        ?.match?.ghost
        ?.positionAt(session.raceElapsed(state.elapsedNanos))
    // The gap is measured and published by the foreground service, so it keeps
    // running while this screen is gone. The screen only draws what it is told.
    val liveDelta: Int? = if (session != null && state.phase == Phase.RECORDING) holderDelta else null
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

    fun attachCaptured(file: java.io.File, kind: String, keepGalleryCopy: Boolean = true) {
        val activityId = state.activityId
        if (activityId == null) return
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
                if (keepGalleryCopy) {
                    if (kind == "VIDEO") {
                        MediaCapture.saveVideoToGallery(context, file)
                    } else {
                        MediaCapture.savePhotoToGallery(context, file)
                    }
                }
            }
            mediaFlash = true
            delay(2500)
            mediaFlash = false
        }
    }

    /** One entry point: the last camera mode used decides whether it opens on photo or video. */
    fun openCamera() {
        cameraMode = if (prefs.getBoolean("camera_last_video", false)) "VIDEO" else "PHOTO"
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val kind = if (context.contentResolver.getType(uri)?.startsWith("video") == true) {
                "VIDEO"
            } else {
                "PHOTO"
            }
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    MediaCapture.importFromGallery(context, uri, kind)
                }
                if (file != null) attachCaptured(file, kind, keepGalleryCopy = false)
            }
        }
    }

    fun importFromGallery() {
        runCatching {
            importLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
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
                    fitPlan = true,
                    ghostRoute = if (state.phase == Phase.RECORDING || state.phase == Phase.PAUSED) {
                        session?.route ?: emptyList()
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
                ProCta(
                    onClick = onPaywall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 12.dp),
                )
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
                            .padding(end = 12.dp, top = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (ghostNoMatch && session == null && state.phase == Phase.RECORDING) {
                    Text(
                        text = extraText(lang, "ghostNoMatch"),
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                if (session != null && liveDelta != null && state.phase == Phase.RECORDING) {
                    val d = liveDelta
                    val ahead = d >= 0
                    val done = session.ghostFinished(state.elapsedNanos)
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
                        Text(
                            if (done) extraText(lang, "ghostFinished") else t.ghost,
                            color = Palette.textSecondary,
                            fontSize = 11.sp,
                        )
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
                        StatCell(speedText, t.speed, onClick = { showSpeedometer = true })
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
                    .padding(bottom = with(LocalDensity.current) { barHeightPx.toDp() } + 4.dp),
            ) {
                if (state.activityId != null &&
                    (state.phase == Phase.RECORDING || state.phase == Phase.PAUSED)
                ) {
                    MediaCaptureRow(
                        t = t,
                        onCamera = { openCamera() },
                        onGallery = { importFromGallery() },
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

            if (showSpeedometer) {
                SpeedometerScreen(onBack = { showSpeedometer = false })
            }

            viewerMedia?.let { m ->
                if (m.kind == "PHOTO") {
                    BackHandler { viewerMedia = null }
                    PhotoViewer(
                        media = m,
                        description = t.photo,
                        captionLabel = t.caption,
                        deleteLabel = t.delete,
                        onSaveCaption = { cap ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppGraph.repository(context).setMediaCaption(m.id, cap)
                                }
                                viewerMedia = m.copy(caption = cap)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppGraph.repository(context).deleteMedia(m.id)
                                    MediaCapture.delete(m.filePath)
                                }
                                viewerMedia = null
                            }
                        },
                        onDismiss = { viewerMedia = null },
                    )
                }
            }

            cameraMode?.let { mode ->
                CameraCaptureScreen(
                    title = if (mode == "PHOTO") t.photo else t.video,
                    photoLabel = t.photo,
                    videoLabel = t.video,
                    recordingLabel = t.stop,
                    closeLabel = t.cancel,
                    permissionLabel = t.cameraPermission,
                    errorLabel = t.captureFailed,
                    startInVideo = mode == "VIDEO",
                    onModeChange = { video ->
                        prefs.edit().putBoolean("camera_last_video", video).apply()
                    },
                    onCaptured = { file, kind ->
                        cameraMode = null
                        attachCaptured(file, kind)
                    },
                    onClose = { cameraMode = null },
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    accent: Boolean = false,
    desc: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (desc != null) {
            Modifier.clearAndSetSemantics { contentDescription = desc }
        } else {
            Modifier
        }
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = value,
            color = if (accent) Palette.accent else Palette.text,
            fontSize = 15.sp,
        )
        Text(
            text = if (onClick != null) "$label \u2197" else label,
            color = Palette.textSecondary,
            fontSize = 10.sp,
        )
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

/** Starts a race from a ranked match, anchored at the moment the runner joins it. */
private fun sessionOf(match: GhostMatch, activityId: Long, t0Nanos: Long, baselineM: Double) =
    GhostSession(
        activityId = activityId,
        match = match,
        t0Nanos = t0Nanos,
        baselineM = baselineM,
    )

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

/** Camera and gallery-pick buttons shown while a recording is running. */
@Composable
private fun MediaCaptureRow(
    t: Texts,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CaptureRoundButton(
            description = t.photo + " / " + t.video,
            onClick = onCamera,
        ) { color ->
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
        CaptureRoundButton(description = t.fromGallery, onClick = onGallery) { color ->
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.14f, h * 0.22f),
                size = Size(w * 0.72f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.08f),
                style = Stroke(width = w * 0.08f),
            )
            // A small sun and a ridge, so it reads as a picture rather than a box.
            drawCircle(color = color, radius = w * 0.07f, center = Offset(w * 0.34f, h * 0.40f))
            val ridge = Path().apply {
                moveTo(w * 0.20f, h * 0.72f)
                lineTo(w * 0.42f, h * 0.50f)
                lineTo(w * 0.58f, h * 0.66f)
                lineTo(w * 0.70f, h * 0.56f)
                lineTo(w * 0.80f, h * 0.72f)
                close()
            }
            drawPath(ridge, color)
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
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
        ) {
            icon(Palette.text)
        }
    }
}

