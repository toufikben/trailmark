package dev.toufik.trailmark.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Units
import dev.toufik.trailmark.app.backup.BackupStore
import dev.toufik.trailmark.app.health.HealthSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Language, subscription, map theme and app info. */
@Composable
fun SettingsScreen(lang: Lang, onLang: (Lang) -> Unit, onPaywall: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("map_dark", true)) }
    var imperial by remember { mutableStateOf(Units.imperial) }
    var voice by remember { mutableStateOf(prefs.getBoolean("voice_coach", true)) }
    var autoPauseOn by remember { mutableStateOf(prefs.getBoolean("auto_pause", true)) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    runCatching { BackupStore.write(context, uri) }.getOrDefault(0)
                }
                backupMessage = t.backupDone.replace("%d", n.toString())
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    runCatching { BackupStore.restore(context, uri) }.getOrDefault(-1)
                }
                backupMessage = if (n >= 0) {
                    t.restoreDone.replace("%d", n.toString())
                } else {
                    t.restoreFailed
                }
            }
        }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(t.tabSettings, color = Palette.text, fontSize = 24.sp)
            Spacer(Modifier.height(20.dp))

            LanguageSelector(lang, onLang)
            Spacer(Modifier.height(20.dp))

            SectionLabel(paywallText(lang, "title"))
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
            Spacer(Modifier.height(18.dp))

            SettingsCard {
                SettingsRow(t.mapTheme) {
                    Segmented(
                        aLabel = t.themeDark,
                        aSelected = dark,
                        onA = {
                            dark = true
                            prefs.edit().putBoolean("map_dark", true).apply()
                        },
                        bLabel = t.themeLight,
                        bSelected = !dark,
                        onB = {
                            dark = false
                            prefs.edit().putBoolean("map_dark", false).apply()
                        },
                    )
                }
                CardDivider()
                SettingsRow(t.units) {
                    Segmented(
                        aLabel = t.unitsMetric,
                        aSelected = !imperial,
                        onA = {
                            imperial = false
                            Units.set(context, false)
                        },
                        bLabel = t.unitsImperial,
                        bSelected = imperial,
                        onB = {
                            imperial = true
                            Units.set(context, true)
                        },
                    )
                }
                CardDivider()
                SettingsRow(t.voiceCoach) {
                    SettingsSwitch(checked = voice) {
                        voice = it
                        prefs.edit().putBoolean("voice_coach", it).apply()
                    }
                }
                CardDivider()
                SettingsRow(t.autoPause) {
                    SettingsSwitch(checked = autoPauseOn) {
                        autoPauseOn = it
                        prefs.edit().putBoolean("auto_pause", it).apply()
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel(t.permissions)
            val healthAvailable = remember { HealthSync.available(context) }
            var healthOn by remember { mutableStateOf(prefs.getBoolean("health_sync", true)) }
            var healthGranted by remember { mutableStateOf(false) }
            var locationGranted by remember { mutableStateOf(locationOk(context)) }
            var notificationsGranted by remember { mutableStateOf(notificationsOk(context)) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                locationGranted = locationOk(context)
                notificationsGranted = notificationsOk(context)
            }
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
                        locationGranted = locationOk(context)
                        notificationsGranted = notificationsOk(context)
                        healthScope.launch {
                            healthGranted = healthAvailable && HealthSync.granted(context)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            SettingsCard {
                PermissionRow(
                    label = t.location,
                    granted = locationGranted,
                    grantLabel = t.grant,
                    onGrant = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    CardDivider()
                    PermissionRow(
                        label = t.notifications,
                        granted = notificationsGranted,
                        grantLabel = t.grant,
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            )
                        },
                    )
                }
                CardDivider()
                SettingsRow(t.healthConnect) {
                    when {
                        !healthAvailable -> SmallAction(t.download, accent = false) {
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
                        }

                        !healthGranted -> SmallAction(t.connect, accent = true) {
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
                        }

                        else -> SettingsSwitch(checked = healthOn) {
                            healthOn = it
                            prefs.edit().putBoolean("health_sync", it).apply()
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            SettingsCard {
                SettingsRow(t.weight) {
                    var weightText by remember {
                        mutableStateOf(prefs.getFloat("weight_kg", 70f).toInt().toString())
                    }
                    NumberField(
                        value = weightText,
                        unit = "kg",
                        maxDigits = 3,
                        onValue = { clean ->
                            weightText = clean
                            clean.toIntOrNull()?.let { v ->
                                if (v in 30..250) {
                                    prefs.edit().putFloat("weight_kg", v.toFloat()).apply()
                                }
                            }
                        },
                    )
                }
                CardDivider()
                SettingsRow(t.monthlyGoal) {
                    var goalText by remember { mutableStateOf(prefs.getInt("goal_km", 100).toString()) }
                    NumberField(
                        value = goalText,
                        unit = t.km,
                        maxDigits = 4,
                        onValue = { clean ->
                            goalText = clean
                            clean.toIntOrNull()?.let { v ->
                                if (v in 1..9999) {
                                    prefs.edit().putInt("goal_km", v).apply()
                                }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel(t.dataSection)
            SettingsCard {
                TextButton(
                    onClick = { runCatching { saveBackup.launch("trailmark-backup.zip") } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t.backupSave, color = Palette.accent, fontSize = 14.sp)
                }
                CardDivider()
                TextButton(
                    onClick = { runCatching { openBackup.launch(arrayOf("application/zip")) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t.backupRestore, color = Palette.accent, fontSize = 14.sp)
                }
            }
            backupMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = Palette.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel(t.about)
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
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Palette.textSecondary.copy(alpha = 0.15f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_SENDTO,
                                            Uri.parse(
                                                "mailto:toufik.bendjeddah@gmail.com" +
                                                    "?subject=Trailmark%20feedback",
                                            ),
                                        ),
                                    )
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Email", color = Palette.text, fontSize = 13.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "toufik.bendjeddah@gmail.com",
                            color = Palette.accent,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "-"

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(4.dp, 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Palette.textSecondary, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = Palette.textSecondary.copy(alpha = 0.10f), thickness = 1.dp)
}

@Composable
private fun SettingsRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        control()
    }
}

/** Two-option segmented control: the active segment gets an accent-tinted pill. */
@Composable
private fun Segmented(
    aLabel: String,
    aSelected: Boolean,
    onA: () -> Unit,
    bLabel: String,
    bSelected: Boolean,
    onB: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.bg)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedButton(aLabel, aSelected, onA)
        SegmentedButton(bLabel, bSelected, onB)
    }
}

@Composable
private fun SegmentedButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) Palette.accent.copy(alpha = 0.18f) else Color.Transparent,
        label = "seg-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Palette.accent else Palette.textSecondary,
        label = "seg-fg",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsSwitch(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChecked,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Palette.bg,
            checkedTrackColor = Palette.accent,
            checkedBorderColor = Palette.accent,
            uncheckedThumbColor = Palette.textSecondary,
            uncheckedTrackColor = Palette.bg,
            uncheckedBorderColor = Palette.textSecondary.copy(alpha = 0.40f),
        ),
    )
}

/** Compact accent action used for the Health Connect connect/download states. */
@Composable
private fun SmallAction(label: String, accent: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (accent) Palette.accent.copy(alpha = 0.14f) else Palette.bg,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            color = if (accent) Palette.accent else Palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    grantLabel: String,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text("✓", color = Palette.accent, fontSize = 16.sp)
        } else {
            SmallAction(grantLabel, accent = true, onClick = onGrant)
        }
    }
}

private fun locationOk(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

private fun notificationsOk(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

@Composable
private fun LanguageSelector(lang: Lang, onLang: (Lang) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { open = true },
            color = Palette.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lang.label, color = Palette.text, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("▾", color = Palette.accent, fontSize = 14.sp)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .heightIn(max = 360.dp)
                .background(Palette.surface),
        ) {
            Lang.entries.sortedBy { it.label }.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Text(
                            l.label,
                            color = if (l == lang) Palette.accent else Palette.text,
                            fontSize = 14.sp,
                        )
                    },
                    onClick = {
                        open = false
                        if (l != lang) onLang(l)
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    unit: String,
    maxDigits: Int,
    onValue: (String) -> Unit,
) {
    Surface(color = Palette.bg, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { s -> onValue(s.filter { it.isDigit() }.take(maxDigits)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Palette.text, fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier
                    .width(56.dp)
                    .heightIn(min = 44.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(unit, color = Palette.textSecondary, fontSize = 13.sp)
        }
    }
}
