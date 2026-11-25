package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.toufik.trailmark.app.FREE_OFFLINE_AREAS
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.maps.HttpBytesFetcher
import dev.toufik.trailmark.app.maps.MANIFEST_URL
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.app.maps.OfflineRegions
import dev.toufik.trailmark.app.maps.ROUTING_MANIFEST_URL
import dev.toufik.trailmark.app.maps.SavedRegion
import dev.toufik.trailmark.app.trialClock
import dev.toufik.trailmark.app.plan.GhRouter
import dev.toufik.trailmark.regions.RegionManifest
import dev.toufik.trailmark.regions.RegionPack
import dev.toufik.trailmark.regions.parseManifest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegionsScreen(onBack: () -> Unit, onPaywall: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val premium = LocalPremium.current
    val pro = premium?.isPro(trialClock(context)) == true
    var manifest by remember { mutableStateOf<RegionManifest?>(null) }
    var routingManifest by remember { mutableStateOf<RegionManifest?>(null) }
    var routingFailed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(setOf<String>()) }
    var routingInstalled by remember { mutableStateOf(setOf<String>()) }
    var savedAreas by remember { mutableStateOf(listOf<SavedRegion>()) }
    val manager = remember { MapPacks.manager(context) }
    val routingManager = remember { MapPacks.routingManager(context) }
    val regions = remember { OfflineRegions(context) }
    val packBlocked =
        !pro && savedAreas.size + installed.size + routingInstalled.size >= FREE_OFFLINE_AREAS

    fun refreshInstalled(packs: List<RegionPack>) {
        installed = packs.filter { manager.isInstalled(it) }.map { it.id }.toSet()
    }

    fun refreshRoutingInstalled(packs: List<RegionPack>) {
        routingInstalled = packs.filter { routingManager.isInstalled(it) }.map { it.id }.toSet()
    }

    LaunchedEffect(Unit) {
        savedAreas = regions.list()
        try {
            val text = withContext(Dispatchers.IO) {
                HttpBytesFetcher.fetch(MANIFEST_URL).decodeToString()
            }
            val m = parseManifest(text)
            manifest = m
            refreshInstalled(m.packs)
        } catch (_: Throwable) {
            error = t.loadFailed
        }
        try {
            val text = withContext(Dispatchers.IO) {
                HttpBytesFetcher.fetch(ROUTING_MANIFEST_URL).decodeToString()
            }
            val m = parseManifest(text)
            routingManifest = m
            refreshRoutingInstalled(m.packs)
        } catch (_: Throwable) {
            routingFailed = true
        }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(t.back, color = Palette.accent) }
                Text(t.mapRegions, color = Palette.text, fontSize = 22.sp)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "offline-card") {
                    OfflineAreaCard(
                        blocked = !pro && savedAreas.size >= FREE_OFFLINE_AREAS,
                        onBlocked = onPaywall,
                        onCompleted = {
                            scope.launch { savedAreas = regions.list() }
                        },
                    )
                }
                item(key = "saved-title") {
                    Text(
                        t.downloadedAreas,
                        color = Palette.text,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                    )
                }
                if (savedAreas.isEmpty()) {
                    item(key = "no-areas") {
                        Text(t.noAreas, color = Palette.textSecondary, fontSize = 13.sp)
                    }
                } else {
                    items(savedAreas, key = { it.id }) { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.name, color = Palette.text, fontSize = 15.sp)
                                Text(
                                    "${String.format(Locale.US, "%.1f", r.bytes / 1_048_576.0)} MB · ${r.tiles} ${t.tiles}",
                                    color = Palette.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { regions.delete(r.id) }
                                    savedAreas = regions.list()
                                }
                            }) { Text(t.delete, color = Palette.textSecondary) }
                        }
                    }
                }
                item(key = "routing-title") {
                    Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                        Text(t.offlineRouting, color = Palette.text, fontSize = 18.sp)
                        Text(t.routingHint, color = Palette.textSecondary, fontSize = 12.sp)
                    }
                }
                val rm = routingManifest
                when {
                    rm == null && routingFailed -> item(key = "routing-error") {
                        Text(t.loadFailed, color = Palette.textSecondary, fontSize = 14.sp)
                    }
                    rm == null -> item(key = "routing-none") {
                        Text(t.searching, color = Palette.textSecondary, fontSize = 14.sp)
                    }
                    rm.packs.isEmpty() -> item(key = "routing-empty") {
                        Text(t.noAreas, color = Palette.textSecondary, fontSize = 13.sp)
                    }
                    else -> items(rm.packs, key = { "r-${it.id}" }) { pack ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(pack.name, color = Palette.text, fontSize = 16.sp)
                            Text(
                                "foot, bike · ${String.format(Locale.US, "%.1f", pack.sizeBytes / 1_048_576.0)} MB",
                                color = Palette.textSecondary,
                                fontSize = 12.sp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (pack.id in routingInstalled) {
                                    Text(t.installed, color = Palette.accent, fontSize = 13.sp)
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                routingManager.remove(pack)
                                                GhRouter.removeGraph(context, pack.id)
                                            }
                                            refreshRoutingInstalled(rm.packs)
                                        }
                                    }) { Text(t.delete, color = Palette.textSecondary) }
                                } else {
                                    Button(
                                        enabled = installing == null,
                                        onClick = {
                                            if (packBlocked) {
                                                onPaywall()
                                                return@Button
                                            }
                                            scope.launch {
                                                installing = pack.id
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        routingManager.install(pack)
                                                        GhRouter.unpackInstalled(context)
                                                    }
                                                    refreshRoutingInstalled(rm.packs)
                                                } catch (_: Throwable) {
                                                    error = t.loadFailed
                                                } finally {
                                                    installing = null
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Palette.accent,
                                            contentColor = Palette.onAccent,
                                        ),
                                    ) { Text(if (installing == pack.id) t.installing else t.download) }
                                }
                            }
                        }
                    }
                }
                item(key = "packs-title") {
                    Text(
                        t.prebuiltPacks,
                        color = Palette.text,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                    )
                }
                val m = manifest
                when {
                    error != null -> item(key = "pack-error") {
                        Text(error!!, color = Palette.textSecondary, fontSize = 14.sp)
                    }
                    m == null -> item(key = "pack-loading") {
                        Text(t.searching, color = Palette.textSecondary, fontSize = 14.sp)
                    }
                    else -> items(m.packs, key = { it.id }) { pack ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(pack.name, color = Palette.text, fontSize = 16.sp)
                            Text(
                                "${pack.kind} · ${String.format(Locale.US, "%.1f", pack.sizeBytes / 1_048_576.0)} MB",
                                color = Palette.textSecondary,
                                fontSize = 12.sp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (pack.id in installed) {
                                    Text(t.installed, color = Palette.accent, fontSize = 13.sp)
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { manager.remove(pack) }
                                            refreshInstalled(m.packs)
                                        }
                                    }) { Text(t.delete, color = Palette.textSecondary) }
                                } else {
                                    Button(
                                        enabled = installing == null,
                                        onClick = {
                                            if (packBlocked) {
                                                onPaywall()
                                                return@Button
                                            }
                                            scope.launch {
                                                installing = pack.id
                                                try {
                                                    withContext(Dispatchers.IO) { manager.install(pack) }
                                                    refreshInstalled(m.packs)
                                                } catch (_: Throwable) {
                                                    error = t.loadFailed
                                                } finally {
                                                    installing = null
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Palette.accent,
                                            contentColor = Palette.onAccent,
                                        ),
                                    ) { Text(if (installing == pack.id) t.installing else t.download) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
