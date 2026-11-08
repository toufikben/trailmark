package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.analytics.ZoneScale
import dev.toufik.trailmark.analytics.percentChange
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.distanceUnit
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatPace
import dev.toufik.trailmark.app.formatSpeed
import dev.toufik.trailmark.app.speedUnit
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.data.SegmentEffort
import dev.toufik.trailmark.data.SegmentStat

/** Calm to hard: the zone bars darken toward the accent as effort rises. */
private val ZONE_COLORS = listOf(
    Color(0xFF3A4743),
    Color(0xFF5E7A3C),
    Color(0xFF8DB33A),
    Color(0xFFB4E03B),
    Color(0xFFC6FF3D),
)

private class Period(val count: Int, val distanceM: Double, val nanos: Long)

private fun period(rows: List<ActivityRow>, from: Long, to: Long): Period {
    val r = rows.filter { !it.isOpen && it.startWallMillis >= from && it.startWallMillis < to }
    return Period(r.size, r.sumOf { it.distanceM }, r.sumOf { it.elapsedNanos })
}

/** This week against last week and this month against last month. */
@Composable
internal fun ComparisonCard(t: Texts, rows: List<ActivityRow>, now: Long = System.currentTimeMillis()) {
    val week = startOfWeek(now)
    val month = startOfMonth(now)
    val prevMonth = startOfMonth(month - 1L)
    val pairs = listOf(
        Triple(t.progressWeek, period(rows, week, Long.MAX_VALUE), period(rows, week - WEEK_MILLIS, week)),
        Triple(t.progressMonth, period(rows, month, Long.MAX_VALUE), period(rows, prevMonth, month)),
    )
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).appearIn(2),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t.comparison, color = Palette.text, fontSize = 14.sp)
            pairs.forEach { (label, cur, prev) ->
                Column {
                    Text(label, color = Palette.textSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        CompareCell(
                            "${formatDistance(cur.distanceM)} ${distanceUnit(t)}",
                            percentChange(cur.distanceM, prev.distanceM),
                            Modifier.weight(1f),
                        )
                        CompareCell(
                            formatDuration(cur.nanos),
                            percentChange(cur.nanos.toDouble(), prev.nanos.toDouble()),
                            Modifier.weight(1f),
                        )
                        CompareCell(
                            "${cur.count} ${t.activitiesShort}",
                            percentChange(cur.count.toDouble(), prev.count.toDouble()),
                            Modifier.weight(1f),
                        )
                    }
                    Text(
                        "${t.previousPeriod}: ${formatDistance(prev.distanceM)} ${distanceUnit(t)} · " +
                            "${formatDuration(prev.nanos)} · ${prev.count}",
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareCell(value: String, change: Int?, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(value, color = Palette.text, fontSize = 15.sp)
        val (label, color) = when {
            change == null -> "—" to Palette.textSecondary
            change >= 0 -> "+$change%" to Palette.accent
            else -> "$change%" to Palette.dangerText
        }
        Text(label, color = color, fontSize = 12.sp)
    }
}

/** Moving time per speed (or, for running, pace) zone. */
@Composable
internal fun ZonesCard(t: Texts, title: String, sport: String, times: LongArray, modifier: Modifier = Modifier) {
    val total = times.sum()
    if (total <= 0L) return
    val scale = ZoneScale.forSport(sport)
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Palette.text, fontSize = 14.sp)
            for (i in 0 until scale.count) {
                val frac = times[i].toFloat() / total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        zoneLabel(t, sport, scale, i),
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.width(92.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Palette.bg),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac.coerceIn(0f, 1f))
                                .height(10.dp)
                                .background(ZONE_COLORS[i.coerceAtMost(ZONE_COLORS.size - 1)]),
                        )
                    }
                    Text(
                        "${Math.round(frac * 100)}% · ${formatDuration(times[i])}",
                        color = Palette.text,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp).width(96.dp),
                    )
                }
            }
        }
    }
}

/** Pace bounds for running (slow zone first), speed bounds for everything else. */
private fun zoneLabel(t: Texts, sport: String, scale: ZoneScale, i: Int): String {
    val lo = if (i == 0) null else scale.upperMps[i - 1]
    val hi = if (i < scale.upperMps.size) scale.upperMps[i] else null
    return if (sport == "RUN") {
        fun pace(mps: Double) = formatPace(1000.0 / mps)
        when {
            lo == null -> "> ${pace(hi!!)}"
            hi == null -> "< ${pace(lo)}"
            else -> "${pace(hi)}–${pace(lo)}"
        }
    } else {
        val u = speedUnit(t)
        when {
            lo == null -> "< ${formatSpeed(hi!!)} $u"
            hi == null -> "> ${formatSpeed(lo)} $u"
            else -> "${formatSpeed(lo)}–${formatSpeed(hi)} $u"
        }
    }
}

/** Every effort on one segment, fastest first, with the gap to the best. */
@Composable
internal fun SegmentBoardDialog(
    t: Texts,
    segment: SegmentStat,
    efforts: List<SegmentEffort>,
    onOpenActivity: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lang = LocalLang.current
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text("${t.bestTimesBoard} · ${segment.name}", color = Palette.text, fontSize = 17.sp) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                val best = efforts.firstOrNull()?.elapsedNanos
                efforts.forEachIndexed { i, e ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenActivity(e.activityId) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (i == 0) Palette.accent else Palette.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(formatDate(e.startWallMillis, lang), color = Palette.text, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        if (best != null && i > 0) {
                            Text(
                                "+${formatDuration(e.elapsedNanos - best)}",
                                color = Palette.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        Text(
                            formatDuration(e.elapsedNanos),
                            color = if (i == 0) Palette.accent else Palette.text,
                            fontSize = 14.sp,
                        )
                    }
                }
                if (efforts.isEmpty()) Text(t.noAttemptsYet, color = Palette.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t.done, color = Palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = { if (confirm) onDelete() else confirm = true }) {
                Text(if (confirm) t.deleteSegmentConfirm else t.deleteSegment, color = Palette.dangerText)
            }
        },
    )
}

/** Sets the weekly distance goal in kilometres, like the monthly goal. */
@Composable
internal fun WeekGoalDialog(t: Texts, currentKm: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentKm.toString()) }
    val value = text.trim().toIntOrNull()
    val valid = value != null && value in 1..2000
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text(t.weeklyGoal, color = Palette.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                label = { Text(t.km) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { value?.let(onSave) }) {
                Text(t.done, color = if (valid) Palette.accent else Palette.textSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = Palette.textSecondary) }
        },
    )
}

internal const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
