package dev.toufik.trailmark.app.ui

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette

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
