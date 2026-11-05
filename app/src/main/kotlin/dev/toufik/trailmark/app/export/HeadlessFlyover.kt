package dev.toufik.trailmark.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL_LIGHT
import dev.toufik.trailmark.app.textsFor
import dev.toufik.trailmark.app.ui.FLYOVER_FPS
import dev.toufik.trailmark.app.ui.FlyoverCamera
import dev.toufik.trailmark.app.ui.FlyoverEncoder
import dev.toufik.trailmark.app.ui.FlyoverHud
import dev.toufik.trailmark.app.ui.FlyoverPath
import dev.toufik.trailmark.app.ui.FlyoverPhase
import dev.toufik.trailmark.app.ui.FlyoverStats
import dev.toufik.trailmark.app.ui.PathSample
import dev.toufik.trailmark.app.ui.autoFlyoverSeconds
import dev.toufik.trailmark.app.ui.easeInOut
import dev.toufik.trailmark.app.ui.followZoom
import dev.toufik.trailmark.app.ui.vectorStyle
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.data.MediaRow
import dev.toufik.trailmark.engine.Sport
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

internal const val VIDEO_WIDTH = 720
internal const val VIDEO_HEIGHT = 1280

private const val FLY_TILT = 55.0

/** How many vertices the drawn line may have: more is invisible at 720 px. */
private const val MAX_LINE_VERTICES = 260

/** Route drawn behind the athlete in the follow cameras, in metres. */
private const val TRAIL_BEHIND_M = 260.0

/** Per-second camera heading smoothing: the heading settles over about half a second. */
private const val BEARING_TAU_S = 0.5

private const val SNAPSHOT_TIMEOUT_MS = 25_000L

private val FULL_RECT = Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
private val BITMAP_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)

private val LINE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFFC6FF3D.toInt()
    strokeWidth = 9f
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private val PUCK_FILL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC6FF3D.toInt() }
private val PUCK_RING = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF0E1113.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

/**
 * Renders a flyover without a MapView: every map frame is a MapSnapshotter
 * image, so the export runs inside a foreground service with no window, no
 * surface and no screen kept awake — the user can leave the app while it runs.
 *
 * The route, the cursor and the stats are drawn with Canvas over the map image,
 * projected with the snapshot's own transform, so a tilted camera shows the
 * trail where the map itself drew the road.
 */
internal class HeadlessFlyover(
    private val context: Context,
    private val job: ExportJob,
    private val output: File,
) {
    suspend fun render(
        points: List<StoredPoint>,
        media: List<MediaRow>,
        stats: FlyoverStats,
        onProgress: (done: Int, total: Int, etaNanos: Long?) -> Unit,
    ) {
        val path = FlyoverPath(points)
        require(!path.isEmpty && path.totalM >= 1.0) { "nothing to fly over" }
        val clips = ExportTimeline.clipsFor(media, job.media, path)
        val seconds = if (job.seconds > 0) job.seconds else autoFlyoverSeconds(path.totalM)
        val timeline = ExportTimeline(path, seconds, clips)
        val lang = Lang.entries.firstOrNull { it.code == job.lang } ?: Lang.EN
        val texts = textsFor(lang)
        val sport = Sport.entries.firstOrNull { it.name == job.sport } ?: Sport.RUN
        val hud = FlyoverHud(texts, stats, sport == Sport.RUN, VIDEO_WIDTH, VIDEO_HEIGHT)
        val card = MediaCard(texts)

        val encoder = withContext(Dispatchers.Default) {
            FlyoverEncoder(output, VIDEO_WIDTH, VIDEO_HEIGHT)
        }
        var snapshotter: MapSnapshotter? = null
        var fixed: MapSnapshot? = null
        var previousBase: Bitmap? = null
        try {
            val follow = job.camera != FlyoverCamera.OVERVIEW
            val tilt = if (job.camera == FlyoverCamera.FLY_3D) FLY_TILT else 0.0
            val zoom = followZoom(avgSpeed(path), job.camera == FlyoverCamera.FLY_3D)
            val overview = overviewCamera(path)
            val startCam = followCamera(
                path.at(0.0),
                path.bearingAt(0.0, lookAhead(path)),
                zoom,
                tilt,
            )
            val active = openSnapshotter(path).also { snapshotter = it }
            // A fixed camera needs one image for the whole clip: that is the
            // fastest path, and it also warms the style and the tiles for the
            // following cameras.
            if (!follow) fixed = shoot(active, overview)

            var bearing = path.bearingAt(0.0, lookAhead(path))
            val alpha = 1.0 - exp(-1.0 / FLYOVER_FPS / BEARING_TAU_S)
            var endCam = startCam
            val started = SystemClock.elapsedRealtimeNanos()

            for (i in 0 until timeline.frames) {
                val frame = timeline.frame(i)
                when (frame) {
                    is ExportFrame.Media -> {
                        val image = withContext(Dispatchers.IO) { card.bitmapFor(frame.clip, frame.t) }
                        withContext(Dispatchers.Default) {
                            encoder.frame { canvas -> card.draw(canvas, image, frame.clip, frame.t) }
                        }
                    }
                    is ExportFrame.Map -> {
                        val camera = when {
                            !follow -> overview
                            frame.phase == FlyoverPhase.INTRO ->
                                lerpCamera(overview, startCam, easeInOut(frame.t))
                            frame.phase == FlyoverPhase.OUTRO ->
                                lerpCamera(endCam, overview, easeInOut(frame.t))
                            else -> {
                                val target = path.bearingAt(frame.distanceM, lookAhead(path))
                                val delta = (target - bearing + 540.0) % 360.0 - 180.0
                                bearing = (bearing + delta * alpha + 360.0) % 360.0
                                followCamera(path.at(frame.distanceM), bearing, zoom, tilt)
                                    .also { endCam = it }
                            }
                        }
                        val shot = if (follow) shoot(active, camera) else null
                        val projection = shot ?: fixed ?: error("map image missing")
                        val base = projection.bitmap
                        val trail = if (frame.phase == FlyoverPhase.INTRO) {
                            emptyList()
                        } else {
                            routeScreen(projection, path, frame.distanceM, if (follow) TRAIL_BEHIND_M else 0.0)
                        }
                        val here = if (frame.phase == FlyoverPhase.INTRO) {
                            null
                        } else {
                            project(projection, path.at(frame.distanceM))
                        }
                        withContext(Dispatchers.Default) {
                            encoder.frame { canvas ->
                                canvas.drawBitmap(base, null, FULL_RECT, BITMAP_PAINT)
                                if (trail.size >= 2) {
                                    val line = Path()
                                    line.moveTo(trail[0].x, trail[0].y)
                                    for (k in 1 until trail.size) line.lineTo(trail[k].x, trail[k].y)
                                    canvas.drawPath(line, LINE_PAINT)
                                }
                                if (here != null) {
                                    canvas.drawCircle(here.x, here.y, 15f, PUCK_FILL)
                                    canvas.drawCircle(here.x, here.y, 15f, PUCK_RING)
                                }
                                drawHud(hud, canvas, frame, path)
                            }
                        }
                        // The encoder uploads the frame asynchronously; freeing the
                        // pixels it was drawn from in the same instant is a race the
                        // GPU can lose. Hold each base one frame longer.
                        if (shot != null) {
                            previousBase?.recycle()
                            previousBase = base
                        }
                    }
                }
                onProgress(i + 1, timeline.frames, eta(started, i + 1, timeline.frames))
            }
            withContext(Dispatchers.Default) { encoder.finish() }
        } catch (t: Throwable) {
            withContext(Dispatchers.Default) { runCatching { encoder.finish() } }
            throw t
        } finally {
            runCatching { previousBase?.recycle() }
            runCatching { fixed?.bitmap?.recycle() }
            card.close()
            runCatching { snapshotter?.cancel() }
        }
    }

    // ---------------------------------------------------------------- maps

    /**
     * The offline style reads a downloaded pack; online it fetches the hosted
     * style. A downloaded pack wins when there is no network, so a render that
     * started indoors still finishes on a plane.
     */
    private fun openSnapshotter(path: FlyoverPath): MapSnapshotter {
        val first = path.at(0.0)
        val pack = MapPacks.packFor(context, first.lat, first.lon)
        val builder = if (pack != null && !isOnline(context)) {
            val glyphs = GlyphStore.ensure(context).absolutePath
            Style.Builder().fromJson(vectorStyle(pack.absolutePath, glyphs, job.dark, job.lang))
        } else {
            Style.Builder().fromUri(if (job.dark) ONLINE_STYLE_URL else ONLINE_STYLE_URL_LIGHT)
        }
        val options = MapSnapshotter.Options(VIDEO_WIDTH, VIDEO_HEIGHT)
            .withStyleBuilder(builder)
            .withPixelRatio(1f)
            .withLogo(false)
        return MapSnapshotter(context, options)
    }

    private suspend fun shoot(snapshotter: MapSnapshotter, camera: CameraPosition): MapSnapshot {
        snapshotter.setCameraPosition(camera)
        val shot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
                snapshotter.start(
                    { snap -> if (cont.isActive) cont.resume(snap) else snap.bitmap.recycle() },
                    { err -> if (cont.isActive) cont.resumeWithException(IllegalStateException(err)) },
                )
            }
        }
        // The snapshotter resets itself only after the callback returns; let that
        // reset run before the next camera is set.
        yield()
        return shot ?: error("map snapshot timed out")
    }

    /**
     * Screen points along the travelled route. In the follow cameras only the
     * stretch just behind the athlete is kept: it is the part that is on screen,
     * and it avoids projecting points that sit behind a tilted camera.
     */
    private fun routeScreen(
        snapshot: MapSnapshot,
        path: FlyoverPath,
        distanceM: Double,
        behindM: Double,
    ): List<PointF> {
        val from = (distanceM - behindM).coerceAtLeast(0.0)
        val span = (distanceM - from).coerceAtLeast(1.0)
        val step = (span / MAX_LINE_VERTICES).coerceAtLeast(2.0)
        val out = ArrayList<PointF>(MAX_LINE_VERTICES + 2)
        var d = from
        while (d < distanceM) {
            project(snapshot, path.at(d))?.let(out::add)
            d += step
        }
        project(snapshot, path.at(distanceM))?.let(out::add)
        return out
    }

    private fun project(snapshot: MapSnapshot, p: PathSample): PointF? {
        val xy = runCatching { snapshot.pixelForLatLng(LatLng(p.lat, p.lon)) }.getOrNull() ?: return null
        if (!xy.x.isFinite() || !xy.y.isFinite()) return null
        // Projections of points behind a tilted camera can land far outside the
        // frame and fold the line back across it.
        if (xy.x < -VIDEO_WIDTH || xy.x > VIDEO_WIDTH * 2f) return null
        if (xy.y < -VIDEO_HEIGHT || xy.y > VIDEO_HEIGHT * 2f) return null
        return xy
    }
}

// ------------------------------------------------------------------ helpers

private fun drawHud(hud: FlyoverHud, c: Canvas, frame: ExportFrame.Map, path: FlyoverPath) {
    when (frame.phase) {
        FlyoverPhase.INTRO -> hud.intro(c, frame.t)
        FlyoverPhase.ROUTE -> hud.route(
            c,
            frame.distanceM,
            path.movingNanosAt(frame.distanceM),
            path.speedAt(frame.distanceM),
            frame.distanceM / path.totalM.coerceAtLeast(1.0),
        )
        FlyoverPhase.OUTRO -> hud.outro(c, frame.t)
    }
}

private fun followCamera(at: PathSample, bearing: Double, zoom: Double, tilt: Double): CameraPosition =
    CameraPosition.Builder().target(LatLng(at.lat, at.lon)).zoom(zoom).bearing(bearing).tilt(tilt).build()

private fun lerpCamera(a: CameraPosition, b: CameraPosition, k: Double): CameraPosition {
    val f = k.coerceIn(0.0, 1.0)
    val ta = a.target ?: return b
    val tb = b.target ?: return a
    val db = (b.bearing - a.bearing + 540.0) % 360.0 - 180.0
    return CameraPosition.Builder()
        .target(
            LatLng(
                ta.latitude + (tb.latitude - ta.latitude) * f,
                ta.longitude + (tb.longitude - ta.longitude) * f,
            ),
        )
        .zoom(a.zoom + (b.zoom - a.zoom) * f)
        .bearing((a.bearing + db * f + 360.0) % 360.0)
        .tilt(a.tilt + (b.tilt - a.tilt) * f)
        .build()
}

private fun lookAhead(path: FlyoverPath): Double = (path.totalM / 60.0).coerceIn(60.0, 300.0)

private fun avgSpeed(path: FlyoverPath): Double =
    if (path.movingNanos > 0) path.totalM / (path.movingNanos / 1e9) else 1.5

/** North-up camera that fits the whole route in the area the stats bar leaves free. */
private fun overviewCamera(path: FlyoverPath): CameraPosition {
    val pts = path.all()
    var minLat = 90.0
    var maxLat = -90.0
    var minLon = 180.0
    var maxLon = -180.0
    pts.forEach {
        if (it.lat < minLat) minLat = it.lat
        if (it.lat > maxLat) maxLat = it.lat
        if (it.lon < minLon) minLon = it.lon
        if (it.lon > maxLon) maxLon = it.lon
    }
    val fit = CameraFit.fit(minLat, minLon, maxLat, maxLon, VIDEO_WIDTH, VIDEO_HEIGHT, 90, 330)
    return CameraPosition.Builder().target(LatLng(fit.lat, fit.lon)).zoom(fit.zoom).bearing(0.0).tilt(0.0).build()
}

private fun eta(startedNanos: Long, done: Int, total: Int): Long? {
    if (done < 5) return null
    val spent = SystemClock.elapsedRealtimeNanos() - startedNanos
    return spent / done * (total - done)
}

/** True when the app can reach the network right now. */
internal fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
