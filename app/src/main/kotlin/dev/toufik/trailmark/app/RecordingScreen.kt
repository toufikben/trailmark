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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
        ghostRoute.lastOrNull { it.timeNanos - ghostStartNanos <= state.elapsedNanos }
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
                if (state.isIdle) {
                    Text(
                        text = t.paused,
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
                    RoundRecordButton(
                        phase = state.phase,
                        description = if (state.phase == Phase.STOPPED) t.start else t.stop,
                        onClick = {
                            when (state.phase) {
                                Phase.RECORDING, Phase.STARTING -> stopService(context)
                                Phase.STOPPED -> {
                                    if (granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                        startService(context, sport)
                                    } else {
                                        launcher.launch(permissions)
                                    }
                                }
                            }
                        },
                    )
                }
                coachLine?.let {
                    Text(text = it, color = Palette.textSecondary, fontSize = 12.sp)
                }
                val g = ghost
                if (g != null && state.phase == Phase.RECORDING) {
                    val d = ghostDeltaM(g, state.distanceM, state.elapsedNanos).toInt()
                    Text(
                        text = "${t.ghost}: ${if (d >= 0) "+" else ""}$d ${t.metre}",
                        color = Palette.accent,
                        fontSize = 12.sp,
                    )
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

/** Small icon-only record/stop button: accent dot to start, red square to stop. */
@Composable
private fun RoundRecordButton(
    phase: Phase,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when (phase) {
        Phase.RECORDING -> Palette.danger
        Phase.STARTING -> Palette.surface
        Phase.STOPPED -> Palette.accent
    }
    val iconTint = if (phase == Phase.STARTING) Palette.text else Color.White
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp),
        ) {
            if (phase == Phase.STOPPED) {
                drawCircle(color = iconTint, radius = size.minDimension / 2f)
            } else {
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

private fun sportLabelOf(t: Texts, s: Sport): String = when (s) {
    Sport.WALK -> t.walk
    Sport.RUN -> t.run
    Sport.BIKE -> t.bike
    Sport.HIKE -> t.hike
}

