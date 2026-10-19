package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.MediaCapture
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.data.MediaRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen photo viewer over a dark scrim with a caption bar; tap the scrim dismisses. */
@Composable
internal fun PhotoViewer(
    media: MediaRow,
    description: String,
    captionLabel: String,
    onSaveCaption: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, media.filePath) {
        value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(media.filePath) }
    }
    var editing by remember { mutableStateOf(false) }
    var text by remember(media.id) { mutableStateOf(media.caption ?: "") }
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
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(Palette.surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.text, fontSize = 14.sp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(captionLabel, color = Palette.textSecondary, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        editing = false
                        onSaveCaption(text.trim().ifEmpty { null })
                    },
                ) {
                    Icon(Icons.Filled.Done, contentDescription = captionLabel, tint = Palette.accent)
                }
            } else {
                Text(
                    media.caption ?: captionLabel,
                    color = if (media.caption.isNullOrBlank()) Palette.textSecondary else Palette.text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = captionLabel, tint = Palette.textSecondary)
                }
            }
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
