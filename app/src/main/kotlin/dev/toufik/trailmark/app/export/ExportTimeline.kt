package dev.toufik.trailmark.app.export

import dev.toufik.trailmark.app.ui.FLYOVER_FPS
import dev.toufik.trailmark.app.ui.FLYOVER_INTRO_FRAMES
import dev.toufik.trailmark.app.ui.FLYOVER_OUTRO_FRAMES
import dev.toufik.trailmark.app.ui.FlyoverPath
import dev.toufik.trailmark.app.ui.FlyoverPhase
import dev.toufik.trailmark.data.MediaRow
import java.io.File

/** How long one photo, and one video at most, holds the screen. */
internal const val PHOTO_SECONDS = 3
internal const val VIDEO_MAX_SECONDS = 6

/** At most this many photos and videos are cut in, so the clip stays watchable. */
internal const val MAX_PHOTOS = 8
internal const val MAX_VIDEOS = 3

/** A media item placed at a point along the route. */
internal class MediaClip(
    val media: MediaRow,
    val atDistanceM: Double,
    val frames: Int,
) {
    val isVideo: Boolean get() = media.kind == "VIDEO"
}

/** One output frame: either the map at a distance, or a media card. */
internal sealed class ExportFrame {
    class Map(val phase: FlyoverPhase, val t: Double, val distanceM: Double) : ExportFrame()
    class Media(val clip: MediaClip, val t: Double) : ExportFrame()
}

/**
 * Maps every output frame to what it shows.
 *
 * The route part is split at the points where photos and videos were taken, so
 * a photo shows up exactly when the flyover reaches the place it was taken —
 * not at a fixed interval that drifts away from the map. Each chunk covers an
 * equal share of *distance*, which keeps the camera moving at a steady speed
 * instead of racing through short chunks.
 */
internal class ExportTimeline(
    private val path: FlyoverPath,
    routeSeconds: Int,
    clips: List<MediaClip>,
) {
    private sealed class Seg(val frames: Int) {
        class MapSeg(
            val fromM: Double,
            val toM: Double,
            val phase: FlyoverPhase,
            frames: Int,
        ) : Seg(frames)

        class MediaSeg(val clip: MediaClip) : Seg(clip.frames)
    }

    private val routeFrames = (routeSeconds.coerceAtLeast(1) * FLYOVER_FPS).coerceAtLeast(2)
    private val segs: List<Seg>

    /** First frame index of each segment, plus the end. */
    private val starts: IntArray

    val frames: Int
    val durationNanos: Long

    init {
        val total = path.totalM
        val placed = clips
            .filter { it.frames > 0 }
            .sortedBy { it.atDistanceM }
            .take(MAX_PHOTOS + MAX_VIDEOS)

        // Where the route is cut. Two clips within two metres share a cut.
        val ends = ArrayList<Double>(placed.size + 1)
        placed.forEach { c ->
            val d = c.atDistanceM.coerceIn(0.0, total)
            if (ends.isEmpty() || d - ends.last() > 2.0) ends.add(d)
        }
        if (ends.isEmpty() || total - ends.last() > 2.0) ends.add(total)
        while (ends.size < 2) ends.add(total)
        ends[ends.size - 1] = total
        for (i in 1 until ends.size) {
            if (ends[i] <= ends[i - 1]) ends[i] = (ends[i - 1] + 0.001).coerceAtMost(total)
        }

        // Frames per chunk, proportional to the distance it covers.
        val weights = DoubleArray(ends.size - 1) { (ends[it + 1] - ends[it]).coerceAtLeast(0.0) }
        val weightSum = weights.sum().takeIf { it > 0.0 } ?: 1.0
        val counts = IntArray(weights.size)
        for (i in weights.indices) counts[i] = (routeFrames * weights[i] / weightSum).toInt()
        var extra = routeFrames - counts.sum()
        var k = 0
        while (extra > 0 && weights.isNotEmpty()) {
            if (weights[k % weights.size] > 0.0) {
                counts[k % weights.size]++
                extra--
            }
            k++
        }

        val out = ArrayList<Seg>(ends.size * 2 + 2)
        out.add(Seg.MapSeg(0.0, 0.0, FlyoverPhase.INTRO, FLYOVER_INTRO_FRAMES))
        var clipIndex = 0
        for (i in 0 until ends.size - 1) {
            out.add(Seg.MapSeg(ends[i], ends[i + 1], FlyoverPhase.ROUTE, counts[i]))
            // A clip is shown right after the chunk that ends where it was taken.
            while (clipIndex < placed.size && placed[clipIndex].atDistanceM <= ends[i + 1] + 2.0) {
                out.add(Seg.MediaSeg(placed[clipIndex]))
                clipIndex++
            }
        }
        while (clipIndex < placed.size) {
            out.add(Seg.MediaSeg(placed[clipIndex]))
            clipIndex++
        }
        out.add(Seg.MapSeg(total, total, FlyoverPhase.OUTRO, FLYOVER_OUTRO_FRAMES))
        segs = out

        starts = IntArray(segs.size + 1)
        for (i in segs.indices) starts[i + 1] = starts[i] + segs[i].frames
        frames = starts.last()
        durationNanos = frames * 1_000_000_000L / FLYOVER_FPS
    }

    fun frame(i: Int): ExportFrame {
        val index = i.coerceIn(0, (frames - 1).coerceAtLeast(0))
        var lo = 0
        var hi = segs.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= index) lo = mid else hi = mid - 1
        }
        val seg = segs[lo]
        val local = index - starts[lo]
        return when (seg) {
            is Seg.MapSeg -> {
                val n = seg.frames.coerceAtLeast(1)
                val t = (local.toDouble() / (n - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
                val d = when (seg.phase) {
                    FlyoverPhase.INTRO -> 0.0
                    FlyoverPhase.OUTRO -> path.totalM
                    FlyoverPhase.ROUTE -> seg.fromM + (seg.toM - seg.fromM) * t
                }
                ExportFrame.Map(seg.phase, t, d)
            }
            is Seg.MediaSeg -> {
                val n = seg.clip.frames.coerceAtLeast(1)
                ExportFrame.Media(seg.clip, (local.toDouble() / (n - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0))
            }
        }
    }

    /** Builds the clips the export can use, honouring the user's media choice. */
    companion object {
        fun clipsFor(media: List<MediaRow>, mode: ExportMedia, path: FlyoverPath): List<MediaClip> {
            if (mode == ExportMedia.OFF) return emptyList()
            val photos = media.filter { it.kind == "PHOTO" && File(it.filePath).isFile }
                .sortedBy { it.elapsedNanos }
                .take(MAX_PHOTOS)
                .map { MediaClip(it, distanceOf(it, path), PHOTO_SECONDS * FLYOVER_FPS) }
            val videos = if (mode == ExportMedia.ALL) {
                media.filter { it.kind == "VIDEO" && File(it.filePath).isFile }
                    .sortedBy { it.elapsedNanos }
                    .take(MAX_VIDEOS)
                    .map {
                        val seconds = ((it.durationMs ?: 0L) / 1000L).toInt()
                            .coerceIn(1, VIDEO_MAX_SECONDS)
                        MediaClip(it, distanceOf(it, path), seconds * FLYOVER_FPS)
                    }
            } else {
                emptyList()
            }
            return (photos + videos).sortedBy { it.atDistanceM }
        }

        /** Where along the route the item belongs, from the row or from its moment. */
        private fun distanceOf(m: MediaRow, path: FlyoverPath): Double {
            if (m.distanceM > 0.0) return m.distanceM.coerceIn(0.0, path.totalM)
            if (m.elapsedNanos > 0L && path.movingNanos > 0L) {
                return (path.totalM * m.elapsedNanos / path.movingNanos).coerceIn(0.0, path.totalM)
            }
            return 0.0
        }
    }
}

