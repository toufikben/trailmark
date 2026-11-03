package dev.toufik.trailmark.app.ui

import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File

/**
 * H.264 encoder fed through its input [Surface]: frames are drawn with a
 * hardware Canvas, so the GPU does colour conversion (no per-pixel YUV loop on
 * the CPU, no stride quirks). Rendering a map frame takes far longer than its
 * 1/30 s of screen time, so presentation times are not taken from the surface
 * clock but rewritten from the output frame count; B-frames are off, so output
 * order is display order.
 */
internal class FlyoverEncoder(
    file: File,
    private val width: Int,
    private val height: Int,
    private val fps: Int = FLYOVER_FPS,
    bitRate: Int = width * height * 6,
) {
    private val frameUs = 1_000_000L / fps
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private val surface: Surface
    private var track = -1
    private var muxerStarted = false
    private var posted = 0L
    private var written = 0L
    private var finished = false

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger("max-bframes", 0)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
    }

    /** Draws one frame. Waits for the encoder to keep up, so no frame queues pile up. */
    fun frame(draw: (Canvas) -> Unit) {
        val canvas = surface.lockHardwareCanvas()
        try {
            draw(canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        posted++
        drain(endOfStream = false)
    }

    fun finish() {
        if (finished) return
        finished = true
        try {
            codec.signalEndOfInputStream()
            drain(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { surface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    val framesWritten: Long get() = written

    private fun drain(endOfStream: Boolean) {
        val start = System.nanoTime()
        while (true) {
            // Mid-stream, stay a few frames behind at most (briefly: some encoders
            // hold a lookahead before emitting); at the end, take everything.
            val behind = posted - written
            val waited = System.nanoTime() - start
            val wait = endOfStream || (behind > MAX_BEHIND && waited < CATCH_UP_NANOS)
            val index = codec.dequeueOutputBuffer(info, if (wait) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!wait) return
                    if (endOfStream && waited > STALL_NANOS) error("encoder never signalled end of stream")
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val out = codec.getOutputBuffer(index)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && muxerStarted && !config) {
                        info.presentationTimeUs = written * frameUs
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(track, out, info)
                        written++
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private companion object {
        const val MAX_BEHIND = 4
        const val CATCH_UP_NANOS = 1_000_000_000L
        const val STALL_NANOS = 8_000_000_000L
    }
}
