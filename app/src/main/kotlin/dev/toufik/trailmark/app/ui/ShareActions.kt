package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands a rendered clip (or any video URI) to the system share sheet. */
fun shareVideo(context: Context, uri: Uri, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

/** Opens a clip the export log remembers, wherever it lives (gallery or app storage). */
fun openVideoUri(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}

/** Parses a URI a log entry stored as text; null when it is empty or malformed. */
fun videoUriOrNull(raw: String): Uri? =
    raw.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }

/** The file name at the end of a content URI, used to label log rows. */
fun fileNameOf(uri: Uri): String = uri.lastPathSegment ?: uri.toString()
