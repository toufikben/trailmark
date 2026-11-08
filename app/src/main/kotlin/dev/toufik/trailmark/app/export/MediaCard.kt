package dev.toufik.trailmark.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.formatDuration
import java.io.File

private const val CARD_RADIUS = 40f
private const val CAPTION_HEIGHT = 104f

/**
 * Draws a photo or a video frame as a rounded card floating over the map, with
 * a caption band and the moment it was taken. Photos are decoded once and kept
 * only while their clip plays; video frames are pulled from the file along the
 * clip's own timebase.
 *
 * The card is transparent where the map shows through, so the caller must draw
 * the map frame first.
 *
 * Bitmaps are recycled one frame late: the encoder uploads a frame on its input
 * surface asynchronously, so freeing the pixels the frame was drawn from in the
 * same instant is a race the GPU can lose.
 */
internal class MediaCard(private val texts: Texts) {

    private var photo: Bitmap? = null
    private var photoId: Long = -1
    private var retriever: MediaMetadataRetriever? = null
    private var retrieverId: Long = -1
    private var videoFrame: Bitmap? = null
    private var pendingRecycle: Bitmap? = null

    private val full = RectF(0f, 0f, VIDEO_WIDTH.toFloat(), VIDEO_HEIGHT.toFloat())
    private val cardRect = RectF(56f, 140f, VIDEO_WIDTH - 56f, VIDEO_HEIGHT - 250f)
    private val imageArea =
        RectF(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom - CAPTION_HEIGHT)
    private val captionArea =
        RectF(cardRect.left, cardRect.bottom - CAPTION_HEIGHT, cardRect.right, cardRect.bottom)
    private val cardPath = Path().apply {
        addRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, Path.Direction.CW)
    }

    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val vignette = Paint().apply { color = 0x70000000.toInt() }
    private val cardBg = Paint().apply { color = 0xF2101412.toInt() }
    private val captionBg = Paint().apply { color = 0xB0000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEF3EF.toInt()
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC6FF3D.toInt()
        textSize = 30f
    }

    /** The bitmap for this frame. Owned by the card; the caller must not recycle it. */
    fun bitmapFor(clip: MediaClip, progress: Double): Bitmap? =
        if (clip.isVideo) videoFrameFor(clip, progress) else photoFor(clip)

    fun draw(canvas: Canvas, bitmap: Bitmap?, clip: MediaClip, progress: Double) {
        // A short fade in and out, so the cut does not flash.
        val edge = minOf(progress.coerceIn(0.0, 1.0), 1.0 - progress.coerceIn(0.0, 1.0))
        val alpha = (edge / 0.05).coerceIn(0.0, 1.0).toFloat()
        if (alpha <= 0.02f) return

        // Dim the map a little behind the card so the card reads as a card.
        vignette.alpha = (120 * alpha).toInt()
        canvas.drawRect(full, vignette)

        canvas.save()
        canvas.clipPath(cardPath)
        cardBg.alpha = (242 * alpha).toInt()
        canvas.drawRect(cardRect, cardBg)
        if (bitmap != null && !bitmap.isRecycled) {
            image.alpha = (255 * alpha).toInt()
            canvas.drawBitmap(bitmap, null, fitIn(bitmap.width, bitmap.height, imageArea), image)
        }
        captionBg.alpha = (170 * alpha).toInt()
        canvas.drawRect(captionArea, captionBg)

        val label = clip.media.caption?.takeIf { it.isNotBlank() }
            ?: if (clip.isVideo) "" else texts.photo
        title.alpha = (255 * alpha).toInt()
        sub.alpha = (255 * alpha).toInt()
        if (label.isNotEmpty()) {
            canvas.drawText(
                ellipsize(label, title, cardRect.width() - 64f),
                cardRect.left + 32f,
                captionArea.top + 52f,
                title,
            )
        }
        canvas.drawText(
            formatDuration(clip.media.elapsedNanos.coerceAtLeast(0L)),
            cardRect.left + 32f,
            captionArea.top + 92f,
            sub,
        )
        canvas.restore()

        border.alpha = (96 * alpha).toInt()
        canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, border)
    }

    fun close() {
        recycle(photo)
        photo = null
        photoId = -1
        recycle(videoFrame)
        videoFrame = null
        recycle(pendingRecycle)
        pendingRecycle = null
        runCatching { retriever?.release() }
        retriever = null
        retrieverId = -1
    }

    private fun photoFor(clip: MediaClip): Bitmap? {
        if (photoId == clip.media.id) return photo
        val old = photo
        photo = null
        recycle(old)
        photo = decode(clip.media.filePath)
        photoId = clip.media.id
        return photo
    }

    private fun videoFrameFor(clip: MediaClip, progress: Double): Bitmap? {
        val r = retrieverFor(clip) ?: return null
        val durationMs = (clip.media.durationMs ?: 0L).coerceAtLeast(1L)
        val us = (durationMs * 1000.0 * progress.coerceIn(0.0, 1.0)).toLong()
        val next = runCatching {
            r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()
        if (next != null) {
            recycle(pendingRecycle)
            pendingRecycle = videoFrame?.takeIf { it !== next }
            videoFrame = next
        }
        return videoFrame
    }

    private fun retrieverFor(clip: MediaClip): MediaMetadataRetriever? {
        if (retrieverId == clip.media.id) return retriever
        runCatching { retriever?.release() }
        retriever = null
        retrieverId = clip.media.id
        retriever = runCatching {
            MediaMetadataRetriever().apply { setDataSource(clip.media.filePath) }
        }.getOrNull()
        return retriever
    }

    private fun recycle(b: Bitmap?) {
        if (b == null || b.isRecycled) return
        runCatching { b.recycle() }
    }

    private fun decode(path: String): Bitmap? {
        val file = File(path)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= VIDEO_WIDTH &&
            bounds.outHeight / (sample * 2) >= VIDEO_HEIGHT
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
    }

    /** The image centred inside [area], never stretched and never cropped. */
    private fun fitIn(w: Int, h: Int, area: RectF): RectF {
        if (w <= 0 || h <= 0) return RectF(area)
        val scale = minOf(area.width() / w, area.height() / h)
        val dw = w * scale
        val dh = h * scale
        val left = area.left + (area.width() - dw) / 2f
        val top = area.top + (area.height() - dh) / 2f
        return RectF(left, top, left + dw, top + dh)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
