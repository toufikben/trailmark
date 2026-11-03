package dev.toufik.trailmark.app.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import dev.toufik.trailmark.analytics.ReplayTrack
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL_LIGHT
import dev.toufik.trailmark.core.model.WaypointType
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

private const val TAG = "TM_BENCH"

/** Some ROMs (ColorOS) drop app logcat lines, so results also go to files/bench.txt. */
private var out: File? = null

private fun report(line: String) {
    Log.i(TAG, line)
    runCatching { out?.appendText(line + "\n") }
}

/**
 * Debug-only timing of the pieces a headless flyover renderer would use:
 * MapSnapshotter frames (cold and warm, 2D and 3D) and surface encoding.
 * Started with `am start ... --el bench_flyover <activityId>`; results go to logcat.
 */
object FlyoverBench {
    fun run(context: Context, scope: CoroutineScope, activityId: Long) {
        out = File(context.filesDir, "bench.txt").apply { delete() }
        report("launched activity=$activityId")
        scope.launch {
            runCatching { bench(context.applicationContext, activityId) }
                .onFailure { report("bench failed: $it") }
        }
    }

    private suspend fun bench(context: Context, activityId: Long) {
        val points = withContext(Dispatchers.IO) {
            AppGraph.repository(context).loadPoints(activityId)
        }.filter { it.type != WaypointType.REJECTED }
        if (points.size < 2) {
            report("activity $activityId has no track")
            return
        }
        val track = ReplayTrack(points)
        report("start activity=$activityId points=${points.size}")
        for (tilt in listOf(0.0, 55.0)) {
            val snapshotter = MapSnapshotter(
                context,
                MapSnapshotter.Options(720, 1280)
                    .withStyleBuilder(Style.Builder().fromUri(ONLINE_STYLE_URL_LIGHT))
                    .withPixelRatio(1f)
                    .withLogo(false),
            )
            val frames = 16
            val times = LongArray(frames)
            for (i in 0 until frames) {
                // Small steps along the route, like consecutive video frames.
                val s = track.sampleAt(track.totalNanos / 4 + i * 2_000_000_000L) ?: continue
                snapshotter.setCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(s.lat, s.lon))
                        .zoom(if (tilt > 0) 15.5 else 16.0)
                        .bearing(s.bearingDeg)
                        .tilt(tilt)
                        .build(),
                )
                val t0 = SystemClock.elapsedRealtime()
                val snap = snapshot(snapshotter)
                times[i] = SystemClock.elapsedRealtime() - t0
                snap.bitmap.recycle()
            }
            snapshotter.cancel()
            val warm = times.drop(1).sorted()
            report(
                "snapshot tilt=$tilt first=${times[0]}ms warmMedian=${warm[warm.size / 2]}ms " +
                    "warmMax=${warm.last()}ms all=${times.joinToString(",")}",
            )
        }
        benchEncoder(context)
        report("done")
    }

    private suspend fun snapshot(s: MapSnapshotter): MapSnapshot =
        suspendCancellableCoroutine { cont ->
            s.start(
                { snap -> if (cont.isActive) cont.resume(snap) },
                { err -> if (cont.isActive) cont.resumeWithException(IllegalStateException(err)) },
            )
        }

    /** 90 frames drawn straight onto the encoder's input surface. */
    private fun benchEncoder(context: Context) {
        val file = File(context.cacheDir, "bench.mp4")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 720, 1280).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val paint = Paint().apply { color = 0xFFC6FF3D.toInt() }
        val info = MediaCodec.BufferInfo()
        val t0 = SystemClock.elapsedRealtime()
        var bytes = 0L
        for (i in 0 until 90) {
            val c: Canvas = surface.lockHardwareCanvas()
            c.drawColor(0xFF0E1113.toInt())
            c.drawCircle(360f, 200f + i * 10f, 40f, paint)
            surface.unlockCanvasAndPost(c)
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, 0)
                if (idx < 0) break
                bytes += info.size
                codec.releaseOutputBuffer(idx, false)
            }
        }
        val ms = SystemClock.elapsedRealtime() - t0
        codec.signalEndOfInputStream()
        codec.stop()
        codec.release()
        surface.release()
        file.delete()
        report("encoder surface 90 frames ${ms}ms (${ms / 90.0}ms/frame) bytes=$bytes")
    }
}
