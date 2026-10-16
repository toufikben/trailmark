package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.data.ActivityRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<ActivityRow>?>(null) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("رجوع ›", color = Palette.accent) }
                Text("سجل الأنشطة", color = Palette.text, fontSize = 22.sp)
            }

            val list = items
            when {
                list == null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator(color = Palette.accent) }

                list.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("لا أنشطة بعد", color = Palette.text, fontSize = 18.sp)
                    Text(
                        "سجّل أول مسار ليظهر هنا",
                        color = Palette.textSecondary,
                        fontSize = 13.sp,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { row ->
                        HistoryRow(row) { onOpen(row.id) }
                        HorizontalDivider(color = Palette.surface)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: ActivityRow, onClick: () -> Unit) {
    val pace = if (row.distanceM < 50) null else (row.elapsedNanos / 1e9) / (row.distanceM / 1000)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(sportLabel(row.sport), color = Palette.text, fontSize = 16.sp)
            Text(formatDate(row.startWallMillis), color = Palette.textSecondary, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("كم ${formatDistance(row.distanceM)}", color = Palette.text, fontSize = 14.sp)
            Text(formatDuration(row.elapsedNanos), color = Palette.text, fontSize = 14.sp)
            Text(pace?.let { formatPace(it) } ?: "--:--", color = Palette.text, fontSize = 14.sp)
            if (row.isOpen) Text("قيد التسجيل", color = Palette.accent, fontSize = 13.sp)
        }
    }
}

private fun sportLabel(sport: String): String = when (sport) {
    "RUN" -> "جري"
    "WALK" -> "مشي"
    "BIKE" -> "دراجة"
    "HIKE" -> "مشي جبلي"
    else -> sport
}
