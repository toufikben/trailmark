package dev.toufik.trailmark.app.ui

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.maps.OfflineRegions
import dev.toufik.trailmark.regions.TileMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

private val RADIUS_CHOICES = listOf(2.0, 5.0, 10.0, 25.0)
private val MAX_ZOOM_CHOICES = listOf(13, 14, 15)
private const val TILE_BUDGET = 3_000L
private const val AVG_TILE_BYTES = 22_000.0
/** Downloads the area around the current position from the global basemap. */
@Composable
fun OfflineAreaCard(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val regions = remember { OfflineRegions(context) }
    var center by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var located by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableStateOf(5.0) }
    var maxZoom by remember { mutableStateOf(14) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        center = withContext(Dispatchers.IO) { lastKnownCenter(context) }
        located = true
    }

    val c = center
    val bbox = c?.let { TileMath.bboxAround(it.first, it.second, radiusKm) }
    val tiles = bbox?.let { TileMath.tileCount(it[0], it[1], it[2], it[3], 11, maxZoom) } ?: 0L
    val sizeMb = tiles * AVG_TILE_BYTES / 1_048_576.0
    val tooBig = tiles > TILE_BUDGET

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(t.offlineArea, color = Palette.text, fontSize = 18.sp)
        when {
            !located -> Text(t.searching, color = Palette.textSecondary, fontSize = 13.sp)
            c == null -> Text(t.needsLocation, color = Palette.textSecondary, fontSize = 13.sp)
            else -> {
                Text(
                    "${t.aroundYou} · ${"%.5f".format(c.first)}, ${"%.5f".format(c.second)}",
                    color = Palette.textSecondary,
                    fontSize = 12.sp,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.radius, color = Palette.textSecondary, fontSize = 13.sp)
                    RADIUS_CHOICES.forEach { r ->
                        FilterChip(
                            selected = radiusKm == r,
                            onClick = { radiusKm = r },
                            label = { Text("${r.toInt()} ${t.km}") },
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.detail, color = Palette.textSecondary, fontSize = 13.sp)
                    MAX_ZOOM_CHOICES.forEach { z ->
                        FilterChip(
                            selected = maxZoom == z,
                            onClick = { maxZoom = z },
                            label = { Text(detailLabel(t, z)) },
                        )
                    }
                }
                Text(
                    "${t.approx} $tiles ${t.tiles} · ${"%.1f".format(sizeMb)} MB",
                    color = if (tooBig) Palette.accent else Palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (tooBig) {
                    Text(t.tooManyTiles, color = Palette.accent, fontSize = 12.sp)
                }
                error?.let { Text(it, color = Palette.accent, fontSize = 12.sp) }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val p = progress
                    Button(
                        enabled = !tooBig && p == null,
                        onClick = {
                            error = null
                            progress = 0
                            scope.launch {
                                try {
                                    val b = bbox ?: return@launch
                                    val bounds = LatLngBounds.Builder()
                                        .include(LatLng(b[1], b[0]))
                                        .include(LatLng(b[3], b[2]))
                                        .build()
                                    regions.download(
                                        name = "${t.aroundYou} · ${radiusKm.toInt()} ${t.km}",
                                        bounds = bounds,
                                        minZoom = 11.0,
                                        maxZoom = maxZoom.toDouble(),
                                        onProgress = { progress = it },
                                    )
                                    onCompleted()
                                } catch (e: Throwable) {
                                    error = e.message ?: t.loadFailed
                                } finally {
                                    progress = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.accent,
                            contentColor = Palette.onAccent,
                        ),
                    ) { Text(t.downloadArea) }
                    if (p != null) {
                        Text(
                            "  ${t.installing} $p%",
                            color = Palette.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun detailLabel(t: Texts, zoom: Int): String = when (zoom) {
    13 -> t.detailLight
    14 -> t.detailNormal
    else -> t.detailFine
}

private fun lastKnownCenter(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var best: Location? = null
    for (p in listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )) {
        val loc = try {
            lm.getLastKnownLocation(p)
        } catch (_: SecurityException) {
            null
        } ?: continue
        if (best == null || loc.time > best.time) best = loc
    }
    return best?.let { it.latitude to it.longitude }
}
