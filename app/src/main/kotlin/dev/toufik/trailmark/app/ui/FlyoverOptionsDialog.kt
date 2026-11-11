package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.export.ExportMedia

/** Clip length, camera and media, picked before a flyover export starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlyoverOptionsDialog(
    t: Texts,
    lang: dev.toufik.trailmark.app.Lang,
    initial: FlyoverOptions,
    onStart: (FlyoverOptions) -> Unit,
    onHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableStateOf(initial.seconds) }
    var camera by remember { mutableStateOf(initial.camera) }
    var media by remember { mutableStateOf(initial.media) }
    var gallery by remember { mutableStateOf(initial.galleryCopy) }
    var pois by remember { mutableStateOf(initial.pois) }
    var fullTrail by remember { mutableStateOf(initial.fullTrail) }
    var cinematic by remember { mutableStateOf(initial.cinematic) }
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
                Text(t.mediaInClip, color = Palette.textSecondary, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ExportMedia.OFF to t.mediaNone,
                        ExportMedia.PHOTOS to t.mediaPhotos,
                        ExportMedia.ALL to t.mediaAll,
                    ).forEach { (m, label) ->
                        FilterChip(
                            selected = media == m,
                            onClick = { media = m },
                            label = { Text(label, fontSize = 12.sp) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t.galleryCopy,
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = gallery, onCheckedChange = { gallery = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t.poiLayer,
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = pois, onCheckedChange = { pois = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dev.toufik.trailmark.app.extraText(lang, "fullTrail"),
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = fullTrail, onCheckedChange = { fullTrail = it })
                }
                Text(
                    dev.toufik.trailmark.app.extraText(lang, "fullTrailHint"),
                    color = Palette.textSecondary,
                    fontSize = 11.sp,
                )
                if (media != ExportMedia.OFF) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dev.toufik.trailmark.app.extraText(lang, "cinematic"),
                            color = Palette.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = cinematic, onCheckedChange = { cinematic = it })
                    }
                    Text(
                        dev.toufik.trailmark.app.extraText(lang, "cinematicHint"),
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Text(t.exportInBackground, color = Palette.textSecondary, fontSize = 11.sp)
                TextButton(onClick = onHistory) {
                    Text(t.exportHistory, color = Palette.accent, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(
                        FlyoverOptions(
                            seconds = seconds,
                            camera = camera,
                            media = media,
                            galleryCopy = gallery,
                            pois = pois,
                            fullTrail = fullTrail,
                            cinematic = cinematic,
                        ),
                    )
                },
            ) {
                Text(t.startExport, color = Palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = Palette.textSecondary) }
        },
    )
}
