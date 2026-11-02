package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import dev.toufik.trailmark.analytics.ReplayTrack
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL_LIGHT
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.Sport
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
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
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val VIDEO_WIDTH = 720
private const val VIDEO_HEIGHT = 1280

/** Camera values copied from the replay map, so the clip matches what you watched. */
private const val FLYOVER_TILT = 55.0
private const val FLYOVER_ZOOM = 15.5
private const val REPLAY_ZOOM = 16.0
private const val BEARING_SMOOTHING = 0.35

/**
 * Renders a 9:16 flyover of [points] and hands the finished MP4 to [onDone].
 * The clip follows the athlete marker like the replay map, honours the chosen
 * [speed] and [flyover] tilt, and lasts `activity / speed`.
 */
@Composable
fun FlyoverOverlay(
    points: List<StoredPoint>,
    sport: Sport,
    packPath: String?,
    dark: Boolean,
    langCode: String,
    speed: Int,
    flyover: Boolean,
    onDone: (File) -> Unit,
    onCancel: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val view = LocalView.current
    var progress by remember { mutableStateOf(0f) }

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
    val route = remember(points) {
        points.filter { it.type != WaypointType.REJECTED && it.lat.isFinite() && it.lon.isFinite() }
    }
    val track = remember(route) { ReplayTrack(route) }
    val plan = remember(track, speed) { flyoverPlan(track.totalNanos, speed) }

    LaunchedEffect(Unit) {
        try {
            while (mapView.width == 0) delay(50)
            val map = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<MapLibreMap> { cont -> mapView.getMapAsync { cont.resume(it) } }
            }
            val lngLats = route.map { Point.fromLngLat(it.lon, it.lat) }
            withContext(Dispatchers.Main) {
                val builder = when {
                    packPath != null && !isOnline(context) ->
                        Style.Builder().fromJson(vectorStyle(packPath, glyphsBase, dark, langCode))
                    else -> Style.Builder().fromUri(if (dark) ONLINE_STYLE_URL else ONLINE_STYLE_URL_LIGHT)
                }
                suspendCancellableCoroutine<Unit> { cont ->
                    map.setStyle(builder) { style ->
                        addFlyoverLayers(style, lngLats, context, sport, dark)
                        cont.resume(Unit)
                    }
                }
            }
            val style = map.style
            val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "trailmark-flyover.mp4")
            val writer = Mp4Writer(file)
            try {
                withContext(Dispatchers.Main) {
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isAttributionEnabled = true
                }
                val tilt = if (flyover) FLYOVER_TILT else 0.0
                val zoom = if (flyover) FLYOVER_ZOOM else REPLAY_ZOOM
                var smoothBearing = track.sampleAt(0L)?.bearingDeg ?: 0.0
                for (i in 0 until plan.frames) {
                    val sample = track.sampleAt(plan.activityNanos(i))
                    if (sample != null) {
                        var delta = (sample.bearingDeg - smoothBearing + 540.0) % 360.0 - 180.0
                        smoothBearing = (smoothBearing + delta * BEARING_SMOOTHING + 360.0) % 360.0
                    }
                    val at = sample?.let { LatLng(it.lat, it.lon) }
                    val shot = withContext(Dispatchers.Main) {
                        if (at != null) {
                            map.cameraPosition = CameraPosition.Builder()
                                .target(at)
                                .zoom(zoom)
                                .bearing(smoothBearing)
                                .tilt(tilt)
                                .build()
                            style?.getSourceAs<GeoJsonSource>(ATHLETE_SOURCE)?.setGeoJson(
                                FeatureCollection.fromFeature(
                                    Feature.fromGeometry(Point.fromLngLat(at.longitude, at.latitude)),
                                ),
                            )
                            style?.getLayerAs<SymbolLayer>(ATHLETE_LAYER)?.setProperties(
                                iconRotate(smoothBearing.toFloat()),
                                iconImage(spriteImageId(sport, i % SPRITE_FRAMES)),
                            )
                        }
                        awaitMapIdle(map)
                        captureMap(map)
                    }
                    val portrait = withContext(Dispatchers.Default) { scaleToPortrait(shot) }
                    if (portrait !== shot) shot.recycle()
                    withContext(Dispatchers.Default) { writer.encode(portrait, plan.ptsUs(i)) }
                    portrait.recycle()
                    progress = (i + 1f) / plan.frames
                }
                withContext(Dispatchers.Default) { writer.finish() }
            } finally {
                runCatching { writer.finish() }
            }
            onDone(file)
        } catch (_: Throwable) {
            onError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
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
                    "720 × 1280 · 24 fps",
                    color = Palette.textSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    "${formatDuration(plan.durationNanos)} · ×$speed",
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

/** A finished video can be handed straight to the system share sheet. */
fun shareVideo(context: Context, file: File, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? android.net.ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private const val ATHLETE_SOURCE = "fly-athlete-source"
private const val ATHLETE_LAYER = "fly-athlete-layer"

private fun addFlyoverLayers(
    style: Style,
    lngLats: List<Point>,
    context: Context,
    sport: Sport,
    dark: Boolean,
) {
    if (lngLats.size < 2 || style.getSource("fly-route") != null) return
    // Same extruded buildings as the replay map, so the tilt reads as a flyover.
    if (style.getLayer("fly-3d") == null) {
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
    style.addSource(
        GeoJsonSource(
            "fly-route",
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(lngLats))),
        ),
    )
    style.addLayer(
        LineLayer("fly-route-line", "fly-route").withProperties(
            lineColor("#C6FF3D"),
            lineWidth(4f),
            lineOpacity(0.95f),
        ),
    )
    val ends = listOf(lngLats.first(), lngLats.last())
    style.addSource(
        GeoJsonSource("fly-ends", FeatureCollection.fromFeatures(ends.map { Feature.fromGeometry(it) })),
    )
    style.addLayer(
        CircleLayer("fly-ends-dot", "fly-ends").withProperties(
            circleColor("#C6FF3D"),
            circleRadius(5f),
            circleStrokeColor("#0E1113"),
            circleStrokeWidth(2f),
        ),
    )
    // The athlete puck from the replay map, moved along the track frame by frame.
    for (f in 0 until SPRITE_FRAMES) {
        style.addImage(spriteImageId(sport, f), spriteBitmap(context, sport, f))
    }
    style.addSource(
        GeoJsonSource(
            ATHLETE_SOURCE,
            FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(lngLats.first()))),
        ),
    )
    style.addLayer(
        SymbolLayer(ATHLETE_LAYER, ATHLETE_SOURCE).withProperties(
            iconImage(spriteImageId(sport, 0)),
            iconSize(1f),
            iconRotate(0f),
            iconRotationAlignment("map"),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ),
    )
}

private suspend fun awaitMapIdle(map: MapLibreMap, timeoutMs: Long = 1600) {
    val done = CompletableDeferred<Unit>()
    val listener = MapLibreMap.OnCameraIdleListener { done.complete(Unit) }
    map.addOnCameraIdleListener(listener)
    try {
        withTimeoutOrNull(timeoutMs) { done.await() }
    } finally {
        runCatching { map.removeOnCameraIdleListener(listener) }
    }
}

private suspend fun captureMap(map: MapLibreMap): Bitmap =
    suspendCancellableCoroutine { cont ->
        map.snapshot(object : MapLibreMap.SnapshotReadyCallback {
            override fun onSnapshotReady(snapshot: Bitmap) {
                if (cont.isActive) cont.resume(snapshot) else snapshot.recycle()
            }
        })
    }

/** Center-crops the screen-shaped snapshot to 720x1280. */
private fun scaleToPortrait(src: Bitmap): Bitmap {
    val scale = maxOf(VIDEO_WIDTH.toFloat() / src.width, VIDEO_HEIGHT.toFloat() / src.height)
    val sw = (src.width * scale).roundToInt()
    val sh = (src.height * scale).roundToInt()
    val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
    val x = ((sw - VIDEO_WIDTH) / 2).coerceAtLeast(0)
    val y = ((sh - VIDEO_HEIGHT) / 2).coerceAtLeast(0)
    val out = Bitmap.createBitmap(scaled, x, y, VIDEO_WIDTH, VIDEO_HEIGHT)
    if (scaled !== src && scaled !== out) scaled.recycle()
    return out
}

/** Minimal synchronous H.264 encoder writing an MP4 through MediaMuxer. */
private class Mp4Writer(private val file: File, fps: Int = 24) {
    private val frameUs = 1_000_000L / fps
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var lastPtsUs = 0L
    private var finished = false
    private val colorFormat = pickColorFormat(codec)
    private val frameBytes = VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2

    init {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            VIDEO_WIDTH,
            VIDEO_HEIGHT,
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_WIDTH * VIDEO_HEIGHT * 5)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /**
     * Encodes one frame at [ptsUs]. Frames carry their own timestamps because a
     * long activity is rendered sparsely but must still play for its full length.
     */
    fun encode(bitmap: Bitmap, ptsUs: Long) {
        val index = dequeueInput()
        val image = runCatching { codec.getInputImage(index) }.getOrNull()
        if (image != null) {
            fillImage(bitmap, image)
        } else {
            val buf = codec.getInputBuffer(index) ?: error("missing encoder input buffer")
            fillYuv(bitmap, buf)
        }
        codec.queueInputBuffer(index, 0, frameBytes, ptsUs, 0)
        lastPtsUs = ptsUs
        drain(false)
    }

    fun finish() {
        if (finished) return
        finished = true
        val index = dequeueInput()
        codec.queueInputBuffer(index, 0, 0, lastPtsUs + frameUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        runCatching { codec.stop() }
        codec.release()
        if (muxerStarted) runCatching { muxer.stop() }
        muxer.release()
    }

    private fun dequeueInput(): Int {
        while (true) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) return index
        }
    }

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val out = codec.getOutputBuffer(index)
                    if (out != null && info.size > 0 && muxerStarted) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, out, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /**
     * Fills the encoder's YUV image plane by plane, honouring row and pixel strides.
     * Writing raw bytes at fixed offsets produced a green video on devices whose
     * encoder uses a padded luma stride, so the Image API is the source of truth here.
     */
    private fun fillImage(bitmap: Bitmap, image: Image) {
        val argb = IntArray(VIDEO_WIDTH * VIDEO_HEIGHT)
        bitmap.getPixels(argb, 0, VIDEO_WIDTH, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer
        yBuf.position(0)
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until VIDEO_HEIGHT) {
            var c = row * VIDEO_WIDTH
            var pos = row * yRowStride
            repeat(VIDEO_WIDTH) {
                val p = argb[c++]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                yBuf.position(pos)
                yBuf.put((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
                pos += yPixelStride
            }
        }
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        uBuf.position(0)
        vBuf.position(0)
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        for (row in 0 until VIDEO_HEIGHT / 2) {
            var posU = row * uRowStride
            var posV = row * vRowStride
            var c = row * 2 * VIDEO_WIDTH
            repeat(VIDEO_WIDTH / 2) {
                val p1 = argb[c]
                val p2 = argb[c + 1]
                val p3 = argb[c + VIDEO_WIDTH]
                val p4 = argb[c + VIDEO_WIDTH + 1]
                c += 2
                val r = ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) +
                    ((p3 shr 16) and 0xFF) + ((p4 shr 16) and 0xFF)
                val g = ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) +
                    ((p3 shr 8) and 0xFF) + ((p4 shr 8) and 0xFF)
                val b = (p1 and 0xFF) + (p2 and 0xFF) + (p3 and 0xFF) + (p4 and 0xFF)
                uBuf.position(posU)
                uBuf.put((((-38 * r - 74 * g + 112 * b + 512) shr 10) + 128).toByte())
                vBuf.position(posV)
                vBuf.put((((112 * r - 94 * g - 18 * b + 512) shr 10) + 128).toByte())
                posU += uPixelStride
                posV += vPixelStride
            }
        }
    }

    private fun fillYuv(bitmap: Bitmap, buf: java.nio.ByteBuffer) {
        val argb = IntArray(VIDEO_WIDTH * VIDEO_HEIGHT)
        bitmap.getPixels(argb, 0, VIDEO_WIDTH, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
        buf.clear()
        var y = 0
        for (row in 0 until VIDEO_HEIGHT) {
            var c = row * VIDEO_WIDTH
            repeat(VIDEO_WIDTH) {
                val p = argb[c++]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                buf.put(y++, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
            }
        }
        for (row in 0 until VIDEO_HEIGHT step 2) {
            var c = row * VIDEO_WIDTH
            repeat(VIDEO_WIDTH / 2) {
                val p1 = argb[c]
                val p2 = argb[c + 1]
                val p3 = argb[c + VIDEO_WIDTH]
                val p4 = argb[c + VIDEO_WIDTH + 1]
                c += 2
                val r = ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) +
                    ((p3 shr 16) and 0xFF) + ((p4 shr 16) and 0xFF)
                val g = ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) +
                    ((p3 shr 8) and 0xFF) + ((p4 shr 8) and 0xFF)
                val b = (p1 and 0xFF) + (p2 and 0xFF) + (p3 and 0xFF) + (p4 and 0xFF)
                buf.put((((-38 * r - 74 * g + 112 * b + 512) shr 10) + 128).toByte())
                buf.put((((112 * r - 94 * g - 18 * b + 512) shr 10) + 128).toByte())
            }
        }
    }
}

/** Prefers flexible YUV so frames can be written through [MediaCodec.getInputImage]. */
private fun pickColorFormat(codec: MediaCodec): Int {
    val formats = runCatching {
        codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats
    }.getOrDefault(IntArray(0))
    return when {
        formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        formats.isNotEmpty() -> formats.first()
        else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    }
}
