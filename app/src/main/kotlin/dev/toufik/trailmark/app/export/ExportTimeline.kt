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

/** The camera drops onto the spot before a card opens, and climbs back out after it. */
internal const val MEDIA_LEAD_SECONDS = 0.7
internal const val MEDIA_TAIL_SECONDS = 0.8

/** Two shots taken within this distance of each other would show the same place twice. */
internal const val MEDIA_MIN_SPACING_M = 40.0

/** The shortest a card may hold and still read; used when the budget is tight. */
internal const val MEDIA_MIN_HOLD_SECONDS = 1.2

/**
 * How much of the route section media may take. A short clip has no room for
 * three three-second photos, so it gives media a bigger slice; a long ride keeps
 * the route as the subject and hands photos a smaller one.
 */
internal fun mediaShareFor(routeSeconds: Int): Double = when {
    routeSeconds <= 20 -> 0.55
    routeSeconds >= 60 -> 0.35
    else -> 0.55 - (routeSeconds - 20) * (0.20 / 40.0)
}

/** How a media card is presented: on its own, or with a descent and a climb-out around it. */
enum class MediaStage { LEAD, HOLD, TAIL }

/** A media item placed at a point along the route. */
internal class MediaClip(
    val media: MediaRow,
    val atDistanceM: Double,
    val holdFrames: Int,
    val leadFrames: Int,
    val tailFrames: Int,
) {
    val isVideo: Boolean get() = media.kind == "VIDEO"
    val frames: Int get() = holdFrames + leadFrames + tailFrames
}

/** One output frame: either the map at a distance, or a media card. */
internal sealed class ExportFrame {
    class Map(val phase: FlyoverPhase, val t: Double, val distanceM: Double) : ExportFrame()
    class Media(
        val clip: MediaClip,
        val t: Double,
        val stage: MediaStage,
        /** Progress inside the current stage: 0 at its first frame, 1 at its last. */
        val stageT: Double,
    ) : ExportFrame()
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

    private val lengthSeconds = routeSeconds.coerceAtLeast(1)
    private val routeFrames = (lengthSeconds * FLYOVER_FPS).coerceAtLeast(2)
    private val segs: List<Seg>

    /** First frame index of each segment, plus the end. */
    private val starts: IntArray

    val frames: Int
    val durationNanos: Long

    init {
        val total = path.totalM
        val placed = placeClips(clips)

        // Where the route is cut. The route always starts at zero, otherwise a
        // route without clips has no chunk to render and the flight is empty.
        // Two clips within two metres share a cut.
        val ends = ArrayList<Double>(placed.size + 2)
        ends.add(0.0)
        placed.forEach { c ->
            val d = c.atDistanceM.coerceIn(0.0, total)
            if (d - ends.last() > 2.0) ends.add(d)
        }
        if (total - ends.last() > 2.0 || ends.size < 2) ends.add(total)
        ends[ends.size - 1] = total
        for (i in 1 until ends.size) {
            if (ends[i] <= ends[i - 1]) ends[i] = (ends[i - 1] + 0.001).coerceAtMost(total)
        }

        // Frames per chunk, proportional to the distance it covers.
        val weights = DoubleArray(ends.size - 1) { (ends[it + 1] - ends[it]).coerceAtLeast(0.0) }
        val weightSum = weights.sum()
        val counts = IntArray(weights.size)
        if (weightSum > 0.0) {
            for (i in weights.indices) counts[i] = (routeFrames * weights[i] / weightSum).toInt()
            var extra = routeFrames - counts.sum()
            var k = 0
            // Every extra frame goes to a chunk with distance, so the loop
            // always removes one; the bound is a guard, not the exit condition.
            while (extra > 0 && k <= weights.size * routeFrames) {
                if (weights[k % weights.size] > 0.0) {
                    counts[k % weights.size]++
                    extra--
                }
                k++
            }
        } else if (counts.isNotEmpty()) {
            // A degenerate path: keep the whole route in the first chunk.
            counts[0] = routeFrames
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
                val clip = seg.clip
                val n = clip.frames.coerceAtLeast(1)
                val t = (local.toDouble() / (n - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
                val lead = clip.leadFrames.coerceAtLeast(0)
                val tail = clip.tailFrames.coerceAtLeast(0)
                val stage = when {
                    lead > 0 && local < lead -> MediaStage.LEAD
                    tail > 0 && local >= n - tail -> MediaStage.TAIL
                    else -> MediaStage.HOLD
                }
                val stageT = when (stage) {
                    // The last frame of a stage sits exactly on 1, so the descent
                    // hands over to the hold without a one-frame jump.
                    MediaStage.LEAD -> local.toDouble() / (lead - 1).coerceAtLeast(1)
                    MediaStage.TAIL -> (local - (n - tail)).toDouble() / (tail - 1).coerceAtLeast(1)
                    MediaStage.HOLD -> 0.0
                }
                ExportFrame.Media(clip, t, stage, stageT.coerceIn(0.0, 1.0))
            }
        }
    }

    /**
     * The clips that make the cut, in route order: near-duplicates dropped, then
     * as many clips as the time budget can carry, with the leftover time spread
     * evenly over their holds. Dropping a whole photo is the last resort, and
     * only when even the shortest readable hold does not fit.
     */
    private fun placeClips(clips: List<MediaClip>): List<MediaClip> {
        val sorted = clips.filter { it.frames > 0 }.sortedBy { it.atDistanceM }
        // Spacing and the hard caps are about content, not about time.
        val candidates = ArrayList<MediaClip>(sorted.size)
        for (c in sorted) {
            if (candidates.size >= MAX_PHOTOS + MAX_VIDEOS) break
            val last = candidates.lastOrNull()
            if (last != null && c.atDistanceM - last.atDistanceM < MEDIA_MIN_SPACING_M) continue
            candidates.add(c)
        }
        if (candidates.isEmpty()) return candidates

        val budget = (routeFrames * mediaShareFor(lengthSeconds)).toInt().coerceAtLeast(1)
        val floorHold = (MEDIA_MIN_HOLD_SECONDS * FLYOVER_FPS).toInt()
        fun shortest(c: MediaClip) = c.leadFrames + minOf(floorHold, c.holdFrames) + c.tailFrames

        // Count the clips that fit at the shortest hold that still reads.
        var count = 0
        var spent = 0
        for (c in candidates) {
            val shortestFrames = shortest(c)
            if (count > 0 && spent + shortestFrames > budget) break
            count++
            spent += shortestFrames
        }
        val kept = candidates.take(count)
        val overhead = kept.sumOf { it.leadFrames + it.tailFrames }
        val spare = (budget - overhead).coerceAtLeast(0)
        val perClip = spare / kept.size
        return kept.map { c ->
            // Never longer than the item was cut to, never shorter than a flash.
            val lower = minOf(floorHold, c.holdFrames)
            val hold = perClip.coerceIn(lower, c.holdFrames)
            MediaClip(c.media, c.atDistanceM, hold, c.leadFrames, c.tailFrames)
        }
    }

    /** Builds the clips the export can use, honouring the user's media choice. */
    companion object {
        fun clipsFor(
            media: List<MediaRow>,
            mode: ExportMedia,
            path: FlyoverPath,
            cinematic: Boolean,
        ): List<MediaClip> {
            if (mode == ExportMedia.OFF) return emptyList()
            val lead = if (cinematic) (MEDIA_LEAD_SECONDS * FLYOVER_FPS).toInt() else 0
            val tail = if (cinematic) (MEDIA_TAIL_SECONDS * FLYOVER_FPS).toInt() else 0
            val photos = media.filter { it.kind == "PHOTO" && File(it.filePath).isFile }
                .sortedBy { it.elapsedNanos }
                .take(MAX_PHOTOS)
                .map {
                    MediaClip(
                        it,
                        distanceOf(it, path),
                        PHOTO_SECONDS * FLYOVER_FPS,
                        lead,
                        tail,
                    )
                }
            val videos = if (mode == ExportMedia.ALL) {
                media.filter { it.kind == "VIDEO" && File(it.filePath).isFile }
                    .sortedBy { it.elapsedNanos }
                    .take(MAX_VIDEOS)
                    .map {
                        val seconds = ((it.durationMs ?: 0L) / 1000L).toInt()
                            .coerceIn(1, VIDEO_MAX_SECONDS)
                        MediaClip(it, distanceOf(it, path), seconds * FLYOVER_FPS, lead, tail)
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

