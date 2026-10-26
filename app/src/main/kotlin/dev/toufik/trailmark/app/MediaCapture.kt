package dev.toufik.trailmark.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

    /**
     * Copies a finished MP4 into the public Movies collection so it survives the
     * share sheet and shows up in the phone's gallery / Files app.
     */
    fun saveVideoToGallery(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Trailmark-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Trailmark")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

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
