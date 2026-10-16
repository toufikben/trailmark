package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.maps.HttpBytesFetcher
import dev.toufik.trailmark.app.maps.MANIFEST_URL
import dev.toufik.trailmark.app.maps.MapPacks
import dev.toufik.trailmark.regions.RegionManifest
import dev.toufik.trailmark.regions.RegionPack
import dev.toufik.trailmark.regions.parseManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manifest by remember { mutableStateOf<RegionManifest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(setOf<String>()) }
    val manager = remember { MapPacks.manager(context) }

    fun refreshInstalled(packs: List<RegionPack>) {
        installed = packs.filter { manager.isInstalled(it) }.map { it.id }.toSet()
    }

    LaunchedEffect(Unit) {
        try {
            val text = withContext(Dispatchers.IO) {
                HttpBytesFetcher.fetch(MANIFEST_URL).decodeToString()
            }
            val m = parseManifest(text)
            manifest = m
            refreshInstalled(m.packs)
        } catch (t: Throwable) {
            error = t.message ?: "تعذّر جلب قائمة المناطق"
        }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("رجوع ›", color = Palette.accent) }
                Text("مناطق الخرائط", color = Palette.text, fontSize = 22.sp)
            }

            val m = manifest
            when {
                error != null -> Text(error!!, color = Palette.textSecondary, fontSize = 14.sp)
                m == null -> Text("جارٍ التحميل…", color = Palette.textSecondary, fontSize = 14.sp)
                m.packs.isEmpty() -> Text("لا حِزَم متاحة", color = Palette.textSecondary)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(m.packs, key = { it.id }) { pack ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(pack.name, color = Palette.text, fontSize = 16.sp)
                            Text(
                                "${pack.kind} · ${"%.1f".format(pack.sizeBytes / 1_048_576.0)} MB",
                                color = Palette.textSecondary,
                                fontSize = 12.sp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (pack.id in installed) {
                                    Text("مثبّت", color = Palette.accent, fontSize = 13.sp)
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { manager.remove(pack) }
                                            refreshInstalled(m.packs)
                                        }
                                    }) { Text("حذف", color = Palette.textSecondary) }
                                } else {
                                    Button(
                                        enabled = installing == null,
                                        onClick = {
                                            scope.launch {
                                                installing = pack.id
                                                try {
                                                    withContext(Dispatchers.IO) { manager.install(pack) }
                                                    refreshInstalled(m.packs)
                                                } catch (t: Throwable) {
                                                    error = "فشل التنزيل: ${t.message}"
                                                } finally {
                                                    installing = null
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Palette.accent,
                                            contentColor = Palette.onAccent,
                                        ),
                                    ) { Text(if (installing == pack.id) "…جارٍ التنزيل" else "تنزيل") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
