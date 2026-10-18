package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.data.ActivityRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
private val SPORT_ORDER = listOf("RUN", "WALK", "BIKE", "HIKE")

private val SPORT_COLORS = mapOf(
    "RUN" to Color(0xFFC6FF3D),
    "WALK" to Color(0xFF12A594),
    "BIKE" to Color(0xFFF76808),
    "HIKE" to Color(0xFF8A93A6),
)
private val OTHER_COLOR = Color(0xFF6E7681)

private class Totals {
    var distanceM = 0.0
    var elapsedNanos = 0L
    var count = 0
}

/** Merged stats + charts page: period cards, sport filter, weekly bars, sport split. */
@Composable
fun ProgressScreen() {
    val context = LocalContext.current
    val t = LocalTexts.current
    var rows by remember { mutableStateOf<List<ActivityRow>?>(null) }
    var sport by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
    }

    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Text(t.tabProgress, color = Palette.text, fontSize = 22.sp)
            when (val list = rows) {
                null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Palette.accent) }

                else -> if (list.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(t.noActivities, color = Palette.text, fontSize = 18.sp)
                        Text(t.recordFirst, color = Palette.textSecondary, fontSize = 13.sp)
                    }
                } else {
                    val filtered = if (sport == null) list else list.filter { it.sport == sport }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                    ) {
                        SportFilterRow(t, sport) { sport = it }
                        if (filtered.isEmpty()) {
                            Text(
                                t.noActivities,
                                color = Palette.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        } else {
                            SummaryRow(t, filtered)
                            WeeklyCard(t, filtered)
                            if (sport == null) SportSplitCard(t, list)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SportFilterRow(t: Texts, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(t.progressAllSports, fontSize = 12.sp) },
        )
        SPORT_ORDER.forEach { s ->
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(if (selected == s) null else s) },
                label = { Text(sportLabel(t, s), fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun SummaryRow(t: Texts, rows: List<ActivityRow>) {
    val now = System.currentTimeMillis()
    val cards = listOf(
        t.progressWeek to totalsSince(rows, startOfWeek(now)),
        t.progressMonth to totalsSince(rows, startOfMonth(now)),
        t.progressAllTime to totalsSince(rows, 0L),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEach { (label, totals) ->
            StatCard(t, label, totals, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(t: Texts, label: String, totals: Totals, modifier: Modifier = Modifier) {
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Palette.textSecondary, fontSize = 11.sp, maxLines = 1)
            Text(
                "${formatDistance(totals.distanceM)} ${t.km}",
                color = Palette.accent,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                formatDuration(totals.elapsedNanos),
                color = Palette.text,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${totals.count} ${t.progressActivities}",
                color = Palette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WeeklyCard(t: Texts, rows: List<ActivityRow>) {
    val now = System.currentTimeMillis()
    val thisWeek = startOfWeek(now)
    val sums = (11 downTo 0).map { back ->
        val from = thisWeek - back * WEEK_MS
        sumsInRange(rows, from, from + WEEK_MS)
    }
    val maxSum = sums.max().coerceAtLeast(1.0)
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.progressWeeklyDistance, color = Palette.text, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(t.progressLast12Weeks, color = Palette.textSecondary, fontSize = 11.sp)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 12.dp)
                    .semantics { contentDescription = t.progressWeeklyDistance },
            ) {
                val gap = size.width * 0.025f
                val barW = (size.width - gap * (sums.size - 1)) / sums.size
                sums.forEachIndexed { i, v ->
                    if (v <= 0.0) return@forEachIndexed
                    val frac = (v / maxSum).toFloat().coerceIn(0.04f, 1f)
                    val barH = size.height * frac
                    val x = i * (barW + gap)
                    val color = if (i == sums.size - 1) Palette.accent else Palette.accent.copy(alpha = 0.55f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW * 0.35f),
                    )
                }
            }
            Text(
                "${t.progressBestWeek}: ${formatDistance(sums.max())} ${t.km}",
                color = Palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SportSplitCard(t: Texts, rows: List<ActivityRow>) {
    val bySport = linkedMapOf<String, Double>()
    SPORT_ORDER.forEach { bySport[it] = 0.0 }
    rows.forEach { r ->
        bySport[r.sport] = (bySport[r.sport] ?: 0.0) + r.distanceM
    }
    var total = bySport.values.sum()
    var values = bySport
    if (total <= 0.0) {
        total = rows.size.toDouble()
        values = linkedMapOf()
        SPORT_ORDER.forEach { values[it] = 0.0 }
        rows.forEach { r -> values[r.sport] = (values[r.sport] ?: 0.0) + 1.0 }
    }
    val entries = values.filter { it.value > 0.0 }
    if (entries.isEmpty() || total <= 0.0) return
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(t.progressBySport, color = Palette.text, fontSize = 14.sp)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(
                    modifier = Modifier
                        .size(116.dp)
                        .semantics { contentDescription = t.progressBySport },
                ) {
                    val stroke = size.minDimension * 0.19f
                    val inset = stroke / 2f
                    var start = -90f
                    entries.forEach { (s, v) ->
                        val sweep = (v / total * 360.0).toFloat()
                        drawArc(
                            color = SPORT_COLORS[s] ?: OTHER_COLOR,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke),
                        )
                        start += sweep
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    entries.forEach { (s, v) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp),
                        ) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(SPORT_COLORS[s] ?: OTHER_COLOR)
                            }
                            Text(
                                sportLabel(t, s),
                                color = Palette.text,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(v / total * 100).toInt()}%",
                                color = Palette.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun totalsSince(rows: List<ActivityRow>, fromMillis: Long): Totals {
    val out = Totals()
    for (r in rows) {
        if (r.startWallMillis >= fromMillis) {
            out.distanceM += r.distanceM
            out.elapsedNanos += r.elapsedNanos
            out.count++
        }
    }
    return out
}

private fun sumsInRange(rows: List<ActivityRow>, fromMillis: Long, toMillis: Long): Double {
    var out = 0.0
    for (r in rows) {
        if (r.startWallMillis >= fromMillis && r.startWallMillis < toMillis) out += r.distanceM
    }
    return out
}

/** Week starts on Monday; computed from the day-of-week offset so it never crosses into a neighbour week. */
private fun startOfWeek(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    val daysFromMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    c.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    zeroTime(c)
    return c.timeInMillis
}

private fun startOfMonth(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.DAY_OF_MONTH, 1)
    zeroTime(c)
    return c.timeInMillis
}

private fun zeroTime(c: Calendar) {
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
}

private fun sportLabel(t: Texts, sport: String): String = when (sport) {
    "RUN" -> t.run
    "WALK" -> t.walk
    "BIKE" -> t.bike
    "HIKE" -> t.hike
    else -> sport
}
