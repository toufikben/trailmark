package dev.toufik.trailmark.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** App-private media storage plus the FileProvider URIs the system camera writes to. */
object MediaCapture {
    fun mediaDir(context: Context): File =
        File(context.getExternalFilesDir(null), "media").apply { mkdirs() }

    fun newPhotoFile(context: Context): File =
        File(mediaDir(context), "IMG_${System.currentTimeMillis()}.jpg")

    fun newVideoFile(context: Context): File =
        File(mediaDir(context), "VID_${System.currentTimeMillis()}.mp4")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /** Video length from the container header; null when unreadable. */
    fun videoDurationMs(file: File): Long? = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(file.absolutePath)
        val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        mmr.release()
        d
    } catch (_: Exception) {
        null
    }
}
