package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL_LIGHT
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.engine.Sport
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.exp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionBase
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val VIDEO_WIDTH = 720
private const val VIDEO_HEIGHT = 1280

private const val FLY_TILT = 55.0

/** The travelled line is resampled to at most this many vertices, so updating it each frame stays cheap. */
private const val MAX_LINE_VERTICES = 1_500

/** Per-second camera heading smoothing: the heading settles over about half a second. */
private const val BEARING_TAU_S = 0.5

/**
 * Renders a 9:16 flyover of [points] at a constant 30 fps and hands the finished
 * MP4 to [onDone]. Stops take no screen time, the camera eases in from the whole
 * route and back out to it, and live stats ride along. [options] picks the clip
 * length and the camera: a 3D flyover, a flat follow, or a fixed overview that
 * renders the map once and is by far the quickest for long routes.
 */
@Composable
fun FlyoverOverlay(
    points: List<StoredPoint>,
    sport: Sport,
    packPath: String?,
    dark: Boolean,
    langCode: String,
    options: FlyoverOptions,
    stats: FlyoverStats,
    onDone: (File) -> Unit,
    onCancel: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val view = LocalView.current
    val density = LocalDensity.current
    var progress by remember { mutableStateOf(0f) }
    var etaNanos by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onCancel)
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    val glyphsBase = remember { GlyphStore.ensure(context).absolutePath }
    val path = remember(points) { FlyoverPath(points) }
    val seconds = if (options.seconds > 0) options.seconds else autoFlyoverSeconds(path.totalM)
    val timeline = remember(path, seconds) { FlyoverTimeline(path.totalM, seconds) }

    LaunchedEffect(Unit) {
        var output: File? = null
        try {
            if (path.isEmpty || path.totalM < 1.0) error("nothing to fly over")
            while (mapView.width == 0) delay(50)
            val map = suspendCancellableCoroutine<MapLibreMap> { cont -> mapView.getMapAsync { cont.resume(it) } }
            val line = resample(path)
            val builder = when {
                packPath != null && !isOnline(context) ->
                    Style.Builder().fromJson(vectorStyle(packPath, glyphsBase, dark, langCode))
                else -> Style.Builder().fromUri(if (dark) ONLINE_STYLE_URL else ONLINE_STYLE_URL_LIGHT)
            }
            val style = suspendCancellableCoroutine<Style> { cont ->
                map.setStyle(builder) { s ->
                    addFlyoverLayers(s, line.points, context, sport, dark, options.camera == FlyoverCamera.FLY_3D)
                    cont.resume(s)
                }
            }
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false

            val overview = overviewCamera(map, path)
            val avgSpeed = if (path.movingNanos > 0) path.totalM / (path.movingNanos / 1e9) else 1.5
            val tilted = options.camera == FlyoverCamera.FLY_3D
            val zoom = followZoom(avgSpeed, tilted)
            val tilt = if (tilted) FLY_TILT else 0.0
            val lookAhead = (path.totalM / 60.0).coerceIn(60.0, 300.0)
            val hud = FlyoverHud(t, stats, sport == Sport.RUN, VIDEO_WIDTH, VIDEO_HEIGHT)

            val file = withContext(Dispatchers.IO) { newExportFile(context) }
            output = file
            val encoder = withContext(Dispatchers.Default) { FlyoverEncoder(file, VIDEO_WIDTH, VIDEO_HEIGHT) }
            try {
                val started = SystemClock.elapsedRealtimeNanos()
                if (options.camera == FlyoverCamera.OVERVIEW) {
                    renderOverview(map, mapView, style, overview, line, path, timeline, hud, encoder) { i ->
                        progress = (i + 1f) / timeline.frames
                        etaNanos = eta(started, i + 1, timeline.frames)
                    }
                } else {
                    var bearing = path.bearingAt(0.0, lookAhead)
                    val alpha = 1.0 - exp(-1.0 / FLYOVER_FPS / BEARING_TAU_S)
                    val startCam = followCamera(path.at(0.0), bearing, zoom, tilt)
                    var endCam = startCam
                    var lastCam: CameraPosition? = null
                    var lastShot: Bitmap? = null
                    for (i in 0 until timeline.frames) {
                        val f = timeline.frame(i)
                        val cam = when (f.phase) {
                            FlyoverPhase.INTRO -> lerpCamera(overview, startCam, easeInOut(f.t))
                            FlyoverPhase.ROUTE -> {
                                val target = path.bearingAt(f.distanceM, lookAhead)
                                val delta = (target - bearing + 540.0) % 360.0 - 180.0
                                bearing = (bearing + delta * alpha + 360.0) % 360.0
                                followCamera(path.at(f.distanceM), bearing, zoom, tilt).also { endCam = it }
                            }
                            FlyoverPhase.OUTRO -> lerpCamera(endCam, overview, easeInOut(f.t * 1.6))
                        }
                        // The outro ends on a held camera: the map would not redraw, so
                        // waiting for it would only time out. Reuse the last frame.
                        val held = lastShot
                        val shot = if (held != null && f.phase == FlyoverPhase.OUTRO && cam == lastCam) held else awaitRender(mapView) {
                            map.cameraPosition = cam
                            val here = path.at(f.distanceM)
                            style.getSourceAs<GeoJsonSource>(DONE_SOURCE)?.setGeoJson(
                                lineFeature(line.prefix(f.distanceM, here)),
                            )
                            style.getSourceAs<GeoJsonSource>(ATHLETE_SOURCE)?.setGeoJson(
                                Feature.fromGeometry(Point.fromLngLat(here.lon, here.lat)),
                            )
                            style.getLayerAs<SymbolLayer>(ATHLETE_LAYER)?.setProperties(
                                iconRotate(bearing.toFloat()),
                                iconImage(spriteImageId(sport, (i / 3) % SPRITE_FRAMES)),
                            )
                            style.getLayerAs<SymbolLayer>(ATHLETE_LAYER)?.setProperties(
                                visibility(if (f.phase == FlyoverPhase.INTRO) Property.NONE else Property.VISIBLE),
                            )
                        }
                        withContext(Dispatchers.Default) {
                            encoder.frame { c ->
                                c.drawBitmap(shot, null, android.graphics.Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT), BITMAP_PAINT)
                                drawHudFor(hud, c, f, path, timeline)
                            }
                        }
                        if (shot !== lastShot) lastShot?.recycle()
                        lastShot = shot
                        lastCam = cam
                        progress = (i + 1f) / timeline.frames
                        etaNanos = eta(started, i + 1, timeline.frames)
                    }
                    lastShot?.recycle()
                }
                withContext(Dispatchers.Default) { encoder.finish() }
            } finally {
                // Draining the encoder blocks, so never on the main thread, and it
                // must still run when the user cancels mid-render.
                withContext(NonCancellable + Dispatchers.Default) { runCatching { encoder.finish() } }
            }
            onDone(file)
        } catch (e: CancellationException) {
            output?.let { f -> withContext(NonCancellable + Dispatchers.IO) { f.delete() } }
            throw e
        } catch (_: Throwable) {
            output?.let { f -> withContext(NonCancellable + Dispatchers.IO) { f.delete() } }
            onError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Exactly the video size, so snapshots need no scaling or cropping.
        val w = with(density) { VIDEO_WIDTH.toDp() }
        val h = with(density) { VIDEO_HEIGHT.toDp() }
        AndroidView(factory = { mapView }, modifier = Modifier.align(Alignment.TopStart).requiredSize(w, h))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.bg.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t.exportVideo,
                    color = Palette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    color = Palette.accent,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "⁦720 × 1280 · $FLYOVER_FPS fps⁩",
                    color = Palette.textSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    "${formatDuration(timeline.durationNanos)} · ${etaNanos?.let { "≈ ${formatDuration(it)}" } ?: "…"}",
                    color = Palette.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(
                    progress = { progress },
                    color = Palette.accent,
                    trackColor = Palette.surface,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(56.dp),
                )
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.accent,
                    contentColor = Palette.onAccent,
                ),
                modifier = Modifier.fillMaxWidth(0.7f),
            ) { Text(t.cancel) }
        }
    }
}

private val BITMAP_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)

private fun drawHudFor(hud: FlyoverHud, c: android.graphics.Canvas, f: FlyoverFrame, path: FlyoverPath, tl: FlyoverTimeline) {
    when (f.phase) {
        FlyoverPhase.INTRO -> hud.intro(c, f.t)
        FlyoverPhase.ROUTE -> hud.route(
            c,
            f.distanceM,
            path.movingNanosAt(f.distanceM),
            path.speedAt(f.distanceM),
            f.distanceM / tl.totalM.coerceAtLeast(1.0),
        )
        FlyoverPhase.OUTRO -> hud.outro(c, f.t)
    }
}

/** Remaining render time from the pace so far. */
private fun eta(startedNanos: Long, done: Int, total: Int): Long? {
    if (done < 10) return null
    val spent = SystemClock.elapsedRealtimeNanos() - startedNanos
    return spent / done * (total - done)
}

/**
 * The fixed-camera mode: the map is rendered once at the overview camera and
 * every frame only draws the travelled line and the athlete on top, so even
 * a 100 km ride exports in seconds.
 */
private suspend fun renderOverview(
    map: MapLibreMap,
    mapView: MapView,
    style: Style,
    overview: CameraPosition,
    line: ResampledLine,
    path: FlyoverPath,
    timeline: FlyoverTimeline,
    hud: FlyoverHud,
    encoder: FlyoverEncoder,
    onFrame: (Int) -> Unit,
) {
    val base = awaitRender(mapView) {
        map.cameraPosition = overview
        style.getLayerAs<LineLayer>(DONE_LAYER)?.setProperties(visibility(Property.NONE))
        style.getLayerAs<SymbolLayer>(ATHLETE_LAYER)?.setProperties(visibility(Property.NONE))
    }
    val screen = line.points.map { map.projection.toScreenLocation(LatLng(it.latitude(), it.longitude())) }
    val done = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC6FF3D.toInt()
        strokeWidth = 9f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val puck = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC6FF3D.toInt() }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0E1113.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val gfxPath = android.graphics.Path()
    withContext(Dispatchers.Default) {
        for (i in 0 until timeline.frames) {
            val f = timeline.frame(i)
            val upTo = line.indexAt(f.distanceM)
            val here = map.projectionSafe(screen, line, f.distanceM, upTo)
            gfxPath.reset()
            gfxPath.moveTo(screen[0].x, screen[0].y)
            for (k in 1..upTo) gfxPath.lineTo(screen[k].x, screen[k].y)
            gfxPath.lineTo(here.x, here.y)
            encoder.frame { c ->
                c.drawBitmap(base, null, android.graphics.Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT), BITMAP_PAINT)
                if (f.phase != FlyoverPhase.INTRO) {
                    c.drawPath(gfxPath, done)
                    c.drawCircle(here.x, here.y, 14f, puck)
                    c.drawCircle(here.x, here.y, 14f, ring)
                }
                drawHudFor(hud, c, f, path, timeline)
            }
            withContext(Dispatchers.Main) { onFrame(i) }
        }
    }
    base.recycle()
}

/** Screen position of the cursor, interpolated between the two projected vertices around it. */
@Suppress("UnusedReceiverParameter")
private fun MapLibreMap.projectionSafe(screen: List<PointF>, line: ResampledLine, d: Double, i: Int): PointF {
    if (i >= screen.size - 1) return screen.last()
    val f = ((d - line.distances[i]) / (line.distances[i + 1] - line.distances[i]).coerceAtLeast(1e-9))
        .coerceIn(0.0, 1.0).toFloat()
    return PointF(
        screen[i].x + (screen[i + 1].x - screen[i].x) * f,
        screen[i].y + (screen[i + 1].y - screen[i].y) * f,
    )
}

/** The route resampled at even spacing, with each vertex's distance along it. */
internal class ResampledLine(val points: List<Point>, val distances: DoubleArray) {
    fun indexAt(d: Double): Int {
        var lo = 0
        var hi = distances.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (distances[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun prefix(d: Double, here: PathSample): List<Point> {
        val n = indexAt(d)
        return points.subList(0, n + 1) + Point.fromLngLat(here.lon, here.lat)
    }
}

private fun resample(path: FlyoverPath): ResampledLine {
    val step = (path.totalM / MAX_LINE_VERTICES).coerceAtLeast(4.0)
    val n = (path.totalM / step).toInt() + 1
    val pts = ArrayList<Point>(n + 1)
    val ds = ArrayList<Double>(n + 1)
    for (k in 0 until n) {
        val d = k * step
        val s = path.at(d)
        pts.add(Point.fromLngLat(s.lon, s.lat))
        ds.add(d)
    }
    val end = path.at(path.totalM)
    pts.add(Point.fromLngLat(end.lon, end.lat))
    ds.add(path.totalM)
    return ResampledLine(pts, ds.toDoubleArray())
}

private fun lineFeature(points: List<Point>): Feature =
    Feature.fromGeometry(LineString.fromLngLats(if (points.size >= 2) points else points + points))

private fun followCamera(at: PathSample, bearing: Double, zoom: Double, tilt: Double): CameraPosition =
    CameraPosition.Builder().target(LatLng(at.lat, at.lon)).zoom(zoom).bearing(bearing).tilt(tilt).build()

/** Straight blend between two cameras, turning the short way round. */
private fun lerpCamera(a: CameraPosition, b: CameraPosition, k: Double): CameraPosition {
    val f = k.coerceIn(0.0, 1.0)
    val ta = a.target ?: return b
    val tb = b.target ?: return a
    val db = (b.bearing - a.bearing + 540.0) % 360.0 - 180.0
    return CameraPosition.Builder()
        .target(LatLng(ta.latitude + (tb.latitude - ta.latitude) * f, ta.longitude + (tb.longitude - ta.longitude) * f))
        .zoom(a.zoom + (b.zoom - a.zoom) * f)
        .bearing((a.bearing + db * f + 360.0) % 360.0)
        .tilt(a.tilt + (b.tilt - a.tilt) * f)
        .build()
}

/** North-up camera fitting the whole route inside the area the stats bar leaves free. */
private fun overviewCamera(map: MapLibreMap, path: FlyoverPath): CameraPosition {
    val pts = path.all()
    val bounds = LatLngBounds.Builder().apply { pts.forEach { include(LatLng(it.lat, it.lon)) } }.build()
    val fitted = if (pts.size >= 2) {
        runCatching { map.getCameraForLatLngBounds(bounds, intArrayOf(90, 300, 90, 330)) }.getOrNull()
    } else {
        null
    }
    return fitted ?: CameraPosition.Builder().target(LatLng(pts.first().lat, pts.first().lon)).zoom(15.0).build()
}

/**
 * Applies [change] and waits until the map has loaded what it needs and drawn
 * it (idle), then snapshots. A plain camera-idle wait fired before tiles were in,
 * which put half-drawn and stale frames in the clip.
 */
private suspend fun awaitRender(mapView: MapView, change: () -> Unit): Bitmap {
    val idle = CompletableDeferred<Unit>()
    val listener = MapView.OnDidBecomeIdleListener { idle.complete(Unit) }
    mapView.addOnDidBecomeIdleListener(listener)
    try {
        change()
        withTimeoutOrNull(RENDER_TIMEOUT_MS) { idle.await() }
    } finally {
        mapView.removeOnDidBecomeIdleListener(listener)
    }
    val map = suspendCancellableCoroutine<MapLibreMap> { cont -> mapView.getMapAsync { cont.resume(it) } }
    return captureMap(map)
}

private const val RENDER_TIMEOUT_MS = 4_000L

/** A finished video (gallery or FileProvider URI) can be handed straight to the system share sheet. */
fun shareVideo(context: Context, uri: android.net.Uri, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

private const val EXPORT_PREFIX = "trailmark-flyover"

/**
 * A fresh file per export, so two renders never write the same path. Earlier
 * renders are dropped first: each one was copied to the gallery when it finished.
 */
private fun newExportFile(context: Context): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles { f -> f.name.startsWith(EXPORT_PREFIX) && f.name.endsWith(".mp4") }
        ?.forEach { it.delete() }
    return File(dir, "$EXPORT_PREFIX-${System.currentTimeMillis()}.mp4")
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? android.net.ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private const val REST_SOURCE = "fly-rest-source"
private const val DONE_SOURCE = "fly-done-source"
private const val DONE_LAYER = "fly-done-line"
private const val ATHLETE_SOURCE = "fly-athlete-source"
private const val ATHLETE_LAYER = "fly-athlete-layer"

private fun addFlyoverLayers(
    style: Style,
    line: List<Point>,
    context: Context,
    sport: Sport,
    dark: Boolean,
    buildings: Boolean,
) {
    if (line.size < 2 || style.getSource(REST_SOURCE) != null) return
    if (buildings && style.getLayer("fly-3d") == null) {
        val below = style.layers.firstOrNull { it is SymbolLayer }?.id
        val b3d = FillExtrusionLayer("fly-3d", "openmaptiles").withProperties(
            fillExtrusionColor(if (dark) "#1E2429" else "#D8DBD6"),
            fillExtrusionHeight(
                Expression.coalesce(Expression.get("render_height"), Expression.literal(8f)),
            ),
            fillExtrusionBase(
                Expression.coalesce(Expression.get("render_min_height"), Expression.literal(0f)),
            ),
            fillExtrusionOpacity(0.85f),
        ).apply { minZoom = 14f }
        b3d.withSourceLayer("building")
        if (below != null) style.addLayerBelow(b3d, below) else style.addLayer(b3d)
    }
    // The whole route, dimmed: what is still ahead.
    style.addSource(GeoJsonSource(REST_SOURCE, FeatureCollection.fromFeature(lineFeature(line))))
    style.addLayer(
        LineLayer("fly-rest-line", REST_SOURCE).withProperties(
            lineColor(if (dark) "#EEF3EF" else "#0E1113"),
            lineWidth(4f),
            lineOpacity(0.35f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    // The travelled part, bright, redrawn every frame.
    style.addSource(GeoJsonSource(DONE_SOURCE, FeatureCollection.fromFeature(lineFeature(line.take(2)))))
    style.addLayer(
        LineLayer(DONE_LAYER, DONE_SOURCE).withProperties(
            lineColor("#C6FF3D"),
            lineWidth(6f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    val ends = listOf(line.first(), line.last())
    style.addSource(
        GeoJsonSource("fly-ends", FeatureCollection.fromFeatures(ends.map { Feature.fromGeometry(it) })),
    )
    style.addLayer(
        CircleLayer("fly-ends-dot", "fly-ends").withProperties(
            circleColor("#C6FF3D"),
            circleRadius(6f),
            circleStrokeColor("#0E1113"),
            circleStrokeWidth(2f),
        ),
    )
    for (f in 0 until SPRITE_FRAMES) {
        style.addImage(spriteImageId(sport, f), spriteBitmap(context, sport, f))
    }
    style.addSource(
        GeoJsonSource(ATHLETE_SOURCE, FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line.first())))),
    )
    style.addLayer(
        SymbolLayer(ATHLETE_LAYER, ATHLETE_SOURCE).withProperties(
            iconImage(spriteImageId(sport, 0)),
            iconSize(1.15f),
            iconRotate(0f),
            iconRotationAlignment("map"),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ),
    )
}

/** A snapshot that never arrives (surface gone in the background) fails the export instead of hanging it. */
private const val SNAPSHOT_TIMEOUT_MS = 10_000L

private suspend fun captureMap(map: MapLibreMap): Bitmap =
    withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            map.snapshot(object : MapLibreMap.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: Bitmap) {
                    if (cont.isActive) cont.resume(snapshot) else snapshot.recycle()
                }
            })
        }
    } ?: error("map snapshot timed out")
