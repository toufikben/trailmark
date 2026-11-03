package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts

/** Clip length and camera, picked before a flyover export starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlyoverOptionsDialog(
    t: Texts,
    initial: FlyoverOptions,
    onStart: (FlyoverOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableStateOf(initial.seconds) }
    var camera by remember { mutableStateOf(initial.camera) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text(t.exportVideo, color = Palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.videoLength, color = Palette.textSecondary, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 15, 30, 60).forEach { s ->
                        FilterChip(
                            selected = seconds == s,
                            onClick = { seconds = s },
                            label = { Text(if (s == 0) t.autoLength else "$s ${t.secondsShort}", fontSize = 12.sp) },
                        )
                    }
                }
                Text(t.cameraMode, color = Palette.textSecondary, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        FlyoverCamera.FLY_3D to t.camera3d,
                        FlyoverCamera.FOLLOW_2D to t.camera2d,
                        FlyoverCamera.OVERVIEW to t.cameraOverview,
                    ).forEach { (c, label) ->
                        FilterChip(
                            selected = camera == c,
                            onClick = { camera = c },
                            label = { Text(label, fontSize = 12.sp) },
                        )
                    }
                }
                if (camera == FlyoverCamera.OVERVIEW) {
                    Text(t.cameraOverviewHint, color = Palette.textSecondary, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(FlyoverOptions(seconds, camera)) }) {
                Text(t.startExport, color = Palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = Palette.textSecondary) }
        },
    )
}
