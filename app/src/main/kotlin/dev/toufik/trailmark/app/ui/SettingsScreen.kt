package dev.toufik.trailmark.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Units
import dev.toufik.trailmark.app.health.HealthSync
import kotlinx.coroutines.launch

/** Language, subscription, map theme and app info. */
@Composable
fun SettingsScreen(lang: Lang, onLang: (Lang) -> Unit, onPaywall: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("map_dark", true)) }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(t.tabSettings, color = Palette.text, fontSize = 22.sp)
            Spacer(Modifier.height(24.dp))
            Text(t.language, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LanguagePicker(lang, onLang)
            Spacer(Modifier.height(24.dp))
            Text(paywallText(lang, "title"), color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val premium = LocalPremium.current
            val now = remember { System.currentTimeMillis() }
            val status = when {
                premium?.purchasedProduct != null -> paywallText(lang, "proActive")
                (premium?.trialDaysLeft(now) ?: 0) > 0 ->
                    paywallText(lang, "trialLeft")
                        .replace("%d", (premium?.trialDaysLeft(now) ?: 0).toString())
                else -> paywallText(lang, "trialEnded")
            }
            Surface(
                color = Palette.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onPaywall),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(status, color = Palette.accent, fontSize = 14.sp)
                    Text(
                        paywallText(lang, "subtitle"),
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(t.mapTheme, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Pill {
                ThemeButton(t.themeDark, selected = dark) {
                    dark = true
                    prefs.edit().putBoolean("map_dark", true).apply()
                }
                ThemeButton(t.themeLight, selected = !dark) {
                    dark = false
                    prefs.edit().putBoolean("map_dark", false).apply()
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(t.units, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            var imperial by remember { mutableStateOf(Units.imperial) }
            Pill {
                ThemeButton(t.unitsMetric, selected = !imperial) {
                    imperial = false
                    Units.set(context, false)
                }
                ThemeButton(t.unitsImperial, selected = imperial) {
                    imperial = true
                    Units.set(context, true)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(t.voiceCoach, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            var voice by remember { mutableStateOf(prefs.getBoolean("voice_coach", true)) }
            Pill {
                ThemeButton(t.on, selected = voice) {
                    voice = true
                    prefs.edit().putBoolean("voice_coach", true).apply()
                }
                ThemeButton(t.off, selected = !voice) {
                    voice = false
                    prefs.edit().putBoolean("voice_coach", false).apply()
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(t.healthConnect, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val healthAvailable = remember { HealthSync.available(context) }
            var healthOn by remember { mutableStateOf(prefs.getBoolean("health_sync", true)) }
            var healthGranted by remember { mutableStateOf(false) }
            val healthScope = rememberCoroutineScope()
            val healthContract = if (healthAvailable) {
                runCatching {
                    PermissionController.createRequestPermissionResultContract()
                }.getOrNull()
            } else {
                null
            }
            val healthLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                healthScope.launch {
                    healthGranted = healthAvailable && HealthSync.granted(context)
                }
            }
            LaunchedEffect(healthAvailable) {
                healthGranted = healthAvailable && HealthSync.granted(context)
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, healthAvailable) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        healthScope.launch {
                            healthGranted = healthAvailable && HealthSync.granted(context)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            Pill {
                when {
                    !healthAvailable -> TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "market://details?id=com.google.android.apps.healthdata",
                                    ),
                                ),
                            )
                        }
                    }) { Text(t.download, color = Palette.textSecondary, fontSize = 13.sp) }

                    !healthGranted -> TextButton(onClick = {
                        val contract = healthContract
                        if (contract != null) {
                            runCatching {
                                healthLauncher.launch(
                                    contract.createIntent(
                                        context,
                                        HealthSync.writePermissions,
                                    ),
                                )
                            }
                        }
                    }) { Text(t.connect, color = Palette.accent, fontSize = 13.sp) }

                    else -> {
                        ThemeButton(t.on, selected = healthOn) {
                            healthOn = true
                            prefs.edit().putBoolean("health_sync", true).apply()
                        }
                        ThemeButton(t.off, selected = !healthOn) {
                            healthOn = false
                            prefs.edit().putBoolean("health_sync", false).apply()
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(t.weight, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            var weightText by remember {
                mutableStateOf(prefs.getFloat("weight_kg", 70f).toInt().toString())
            }
            Surface(color = Palette.surface, shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = weightText,
                        onValueChange = { s ->
                            val clean = s.filter { it.isDigit() }.take(3)
                            weightText = clean
                            clean.toIntOrNull()?.let { v ->
                                if (v in 30..250) {
                                    prefs.edit().putFloat("weight_kg", v.toFloat()).apply()
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = Palette.text, fontSize = 16.sp),
                        singleLine = true,
                        modifier = Modifier
                            .width(56.dp)
                            .heightIn(min = 44.dp),
                    )
                    Text("kg", color = Palette.textSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(t.about, color = Palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Palette.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(t.appName, color = Palette.text, fontSize = 15.sp)
                        Text(
                            "${t.version} ${appVersion(context)}",
                            color = Palette.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(t.attribution, color = Palette.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "-"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(lang: Lang, onLang: (Lang) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Lang.entries.forEach { l ->
            val selected = l == lang
            Surface(
                color = if (selected) Palette.accent else Palette.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .clickable { onLang(l) }
                    .heightIn(min = 48.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        l.label,
                        color = if (selected) Palette.bg else Palette.textSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.surface),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ThemeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(
            label,
            color = if (selected) Palette.accent else Palette.textSecondary,
            fontSize = 13.sp,
        )
    }
}
