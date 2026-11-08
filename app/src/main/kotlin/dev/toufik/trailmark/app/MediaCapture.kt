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
    fun saveVideoToGallery(context: Context, file: File): Uri? =
        saveToGallery(context, file, "video/mp4", Environment.DIRECTORY_MOVIES, ".mp4")

    /**
     * Copies a finished JPEG into the public Pictures collection so the phone's
     * gallery shows the shot even if the activity is deleted later.
     */
    fun savePhotoToGallery(context: Context, file: File): Uri? =
        saveToGallery(context, file, "image/jpeg", Environment.DIRECTORY_PICTURES, ".jpg")

    /** One MediaStore insert for both kinds; returns null when the insert fails. */
    private fun saveToGallery(
        context: Context,
        file: File,
        mime: String,
        relative: String,
        suffix: String,
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Trailmark-${System.currentTimeMillis()}$suffix")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/Trailmark")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (mime.startsWith("image")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            val out = resolver.openOutputStream(uri)
            if (out == null) {
                runCatching { resolver.delete(uri, null, null) }
                null
            } else {
                out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }
                uri
            }
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /**
     * Pulls a picture the user picked from the gallery into the app's own
     * folder, so the activity keeps working when the original is moved or the
     * grant expires.
     */
    fun importFromGallery(context: Context, uri: Uri, kind: String): File? = try {
        val photo = kind == "PHOTO"
        val target = if (photo) newPhotoFile(context) else newVideoFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: run {
            target.delete()
            null
        }
        target.takeIf { it.length() > 0L } ?: run { target.delete(); null }
    } catch (_: Exception) {
        null
    }

    /** Deletes a capture from app storage; a gallery copy the user asked for stays. */
    fun delete(filePath: String) {
        runCatching { File(filePath).delete() }
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
