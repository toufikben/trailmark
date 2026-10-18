package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.toufik.trailmark.app.MediaCapture
import dev.toufik.trailmark.data.MediaRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen photo viewer over a dark scrim; a tap anywhere dismisses it. */
@Composable
internal fun PhotoViewer(media: MediaRow, description: String, onDismiss: () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, media.filePath) {
        value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(media.filePath) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(onClick = onDismiss)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

/** Hands a captured video to the system player with a read grant on the FileProvider URI. */
internal fun openVideoPlayer(context: Context, filePath: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(MediaCapture.uriFor(context, File(filePath)), "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}
