package dev.toufik.trailmark.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.analytics.ActivityStat
import dev.toufik.trailmark.analytics.AchievementState
import dev.toufik.trailmark.analytics.AchFamily
import dev.toufik.trailmark.analytics.PersonalRecords
import dev.toufik.trailmark.analytics.evaluateAchievements
import dev.toufik.trailmark.analytics.personalRecords
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.achievementTitle
import dev.toufik.trailmark.app.distanceUnit
import dev.toufik.trailmark.app.elevationUnit
import dev.toufik.trailmark.app.extraText
import dev.toufik.trailmark.app.formatDistance
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.app.formatElevation
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.data.SegmentStat
import dev.toufik.trailmark.data.SegmentEffort
import dev.toufik.trailmark.analytics.ZoneScale
import dev.toufik.trailmark.analytics.zoneTimes
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import android.content.Context

private val SPORT_ORDER = listOf("RUN", "WALK", "BIKE", "HIKE")

private class Totals {
    var distanceM = 0.0
    var elapsedNanos = 0L
    var count = 0
}

/** Merged stats + charts page: period cards, sport filter, weekly bars, sport split. */
@Composable
fun ProgressScreen(onPaywall: () -> Unit, onOpenActivity: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    val goalKm = remember { prefs.getInt("goal_km", 100) }
    var rows by remember { mutableStateOf<List<ActivityRow>?>(null) }
    var records by remember { mutableStateOf<PersonalRecords?>(null) }
    var segments by remember { mutableStateOf<List<SegmentStat>>(emptyList()) }
    var sport by remember { mutableStateOf<String?>(null) }
    var weekGoalKm by remember { mutableStateOf(prefs.getInt("goal_week_km", (goalKm / 4).coerceAtLeast(1))) }
    var editWeekGoal by remember { mutableStateOf(false) }
    var boardFor by remember { mutableStateOf<SegmentStat?>(null) }
    var board by remember { mutableStateOf<List<SegmentEffort>>(emptyList()) }
    var zones by remember { mutableStateOf<Pair<String, LongArray>?>(null) }
    val scope = rememberCoroutineScope()

    // Time in zones over the last 30 days, for the selected sport (or the most used one).
    LaunchedEffect(rows, sport) {
        val list = rows ?: return@LaunchedEffect
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val recent = list.filter { !it.isOpen && it.startWallMillis >= since }
        val zoneSport = sport ?: recent.groupingBy { it.sport }.eachCount().maxByOrNull { it.value }?.key
        if (zoneSport == null) {
            zones = null
            return@LaunchedEffect
        }
        zones = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            val scale = ZoneScale.forSport(zoneSport)
            val acc = LongArray(scale.count)
            recent.filter { it.sport == zoneSport }.take(40).forEach { r ->
                val z = zoneTimes(repo.loadPoints(r.id), scale)
                for (i in acc.indices) acc[i] += z[i]
            }
            zoneSport to acc
        }
    }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { AppGraph.repository(context).listActivities() }
        rows = list
        records = withContext(Dispatchers.IO) {
            val repo = AppGraph.repository(context)
            val routes = list
                .filter { !it.isOpen && it.distanceM >= 1000.0 }
                .sortedByDescending { it.distanceM }
                .take(80)
                .map { repo.loadPoints(it.id) }
            personalRecords(routes)
        }
        segments = withContext(Dispatchers.IO) { AppGraph.repository(context).segmentStats() }
    }

    if (editWeekGoal) {
        WeekGoalDialog(
            t,
            weekGoalKm,
            onSave = { v ->
                prefs.edit().putInt("goal_week_km", v).apply()
                weekGoalKm = v
                editWeekGoal = false
            },
            onDismiss = { editWeekGoal = false },
        )
    }
    boardFor?.let { seg ->
        SegmentBoardDialog(
            t,
            seg,
            board,
            onOpenActivity = { id ->
                boardFor = null
                onOpenActivity(id)
            },
            onDelete = {
                boardFor = null
                scope.launch {
                    segments = withContext(Dispatchers.IO) {
                        val repo = AppGraph.repository(context)
                        repo.deleteSegment(seg.id)
                        repo.segmentStats()
                    }
                }
            },
            onDismiss = { boardFor = null },
        )
    }
    Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.tabProgress, color = Palette.text, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                ProCta(onClick = onPaywall)
            }
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
                            NextGoalCard(t, list)
                            SummaryRow(t, filtered)
                            GoalCard(
                                t,
                                totalsSince(filtered, startOfMonth(System.currentTimeMillis())).distanceM,
                                goalKm,
                            )
                            WeekGoalCard(
                                t,
                                totalsSince(filtered, startOfWeek(System.currentTimeMillis())).distanceM,
                                weekGoalKm,
                                onEdit = { editWeekGoal = true },
                            )
                            ComparisonCard(t, filtered)
                            WeeklyCard(t, filtered)
                            zones?.let { (zs, times) -> ZonesCard(t, t.zonesLast30, zs, times) }
                            if (sport == null) {
                                records?.let { RecordsCard(t, it) }
                                if (segments.isNotEmpty()) {
                                    SegmentsCard(t, segments) { seg ->
                                        scope.launch {
                                            board = withContext(Dispatchers.IO) {
                                                AppGraph.repository(context).effortsForSegment(seg.id)
                                            }
                                            boardFor = seg
                                        }
                                    }
                                }
                                SportSplitCard(t, list)
                            }
                            AchievementsCard(t, list)
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

/**
 * The single closest locked achievement, promoted above the charts: a clear
 * "what is next" target instead of a wall of small badges at the bottom.
 */
@Composable
private fun NextGoalCard(t: Texts, rows: List<ActivityRow>) {
    val lang = LocalLang.current
    val states = remember(rows) {
        evaluateAchievements(
            rows.map {
                ActivityStat(
                    startWallMillis = it.startWallMillis,
                    distanceM = it.distanceM,
                    elapsedNanos = it.elapsedNanos,
                    climbM = it.climbM,
                    sport = it.sport,
                )
            },
        )
    }
    val next = remember(states) {
        states.filter { !it.unlocked }.maxByOrNull { it.fraction() }
    } ?: return
    val unlocked = states.count { it.unlocked }
    val frac = next.fraction().toFloat().coerceIn(0f, 1f)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(
        targetValue = if (shown) frac else 0f,
        animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
        label = "next-goal-grow",
    )
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(0),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.achievements, color = Palette.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("$unlocked/${states.size}", color = Palette.accent, fontSize = 12.sp)
            }
            Text(
                achievementTitle(lang, next.id),
                color = Palette.text,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    achievementProgress(t, next),
                    color = Palette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(frac * 100).toInt()}%",
                    color = Palette.accent,
                    fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Palette.bg),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(grow)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Palette.accent),
                )
            }
        }
    }
}

@Composable
private fun WeekGoalCard(t: Texts, weekM: Double, goalKm: Int, onEdit: () -> Unit) {
    val goalM = goalKm.coerceAtLeast(1) * 1000.0
    val frac = (weekM / goalM).coerceIn(0.0, 1.0)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(
        targetValue = if (shown) frac.toFloat() else 0f,
        animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
        label = "week-goal-grow",
    )
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(1)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.progressWeek, color = Palette.text, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(frac * 100).toInt()}%",
                    color = if (frac >= 1.0) Palette.accent else Palette.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                "${formatDistance(weekM)} / $goalKm ${t.km}",
                color = Palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.bg),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(grow)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.accent.copy(alpha = 0.80f)),
                )
            }
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
        cards.forEachIndexed { i, (label, totals) ->
            StatCard(t, label, totals, modifier = Modifier.weight(1f).appearIn(i))
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
                "${formatDistance(totals.distanceM)} ${distanceUnit(t)}",
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
private fun RecordsCard(t: Texts, records: PersonalRecords) {
    val tiles = listOfNotNull(
        records.best1kSec?.let { "1 ${t.km}" to it },
        records.best5kSec?.let { "5 ${t.km}" to it },
        records.best10kSec?.let { "10 ${t.km}" to it },
    )
    if (tiles.isEmpty()) return
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(3),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(t.records, color = Palette.text, fontSize = 14.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tiles.forEach { (label, sec) ->
                    Surface(
                        color = Palette.bg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(label, color = Palette.textSecondary, fontSize = 11.sp, maxLines = 1)
                            Text(
                                formatDuration((sec * 1_000_000_000L).toLong()),
                                color = Palette.accent,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentsCard(t: Texts, segments: List<SegmentStat>, onOpen: (SegmentStat) -> Unit) {
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(4),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(t.segments, color = Palette.text, fontSize = 14.sp)
            segments.forEach { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpen(s) }
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.name, color = Palette.text, fontSize = 14.sp, maxLines = 1)
                        Text(
                            "${s.attempts} ${t.attempts}",
                            color = Palette.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        s.bestNanos?.let { formatDuration(it) } ?: "--:--",
                        color = Palette.accent,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(t: Texts, monthM: Double, goalKm: Int) {
    val goalM = goalKm.coerceAtLeast(1) * 1000.0
    val frac = (monthM / goalM).coerceIn(0.0, 1.0)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(
        targetValue = if (shown) frac.toFloat() else 0f,
        animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
        label = "goal-grow",
    )
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(1),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.monthlyGoal, color = Palette.text, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(frac * 100).toInt()}%",
                    color = if (frac >= 1.0) Palette.accent else Palette.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                "${formatDistance(monthM)} / $goalKm ${t.km}",
                color = Palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.bg),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(grow)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.accent),
                )
            }
        }
    }
}

@Composable
private fun WeeklyCard(t: Texts, rows: List<ActivityRow>) {
    val now = System.currentTimeMillis()
    val sums = (11 downTo 0).map { back ->
        // Calendar-aware, not thisWeek - n*168h: across a DST change the millisecond
        // maths puts the boundary an hour off and a late Sunday run lands in the
        // wrong bar.
        val from = weekStartBack(now, back)
        val to = weekStartBack(now, back - 1)
        sumsInRange(rows, from, to)
    }
    val maxSum = sums.max().coerceAtLeast(1.0)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
        label = "weekly-grow",
    )
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(2),
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
                    val reveal = (grow * 2.4f - i * 0.12f).coerceIn(0f, 1f)
                    if (reveal <= 0f) return@forEachIndexed
                    val frac = (v / maxSum).toFloat().coerceIn(0.04f, 1f)
                    val barH = size.height * frac * reveal
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
                "${t.progressBestWeek}: ${formatDistance(sums.max())} ${distanceUnit(t)}",
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
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
        label = "donut-grow",
    )
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(3),
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
                        val sweep = (v / total * 360.0).toFloat() * grow
                        drawArc(
                            color = SPORT_COLORS[s] ?: OTHER_SPORT_COLOR,
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
                                drawCircle(SPORT_COLORS[s] ?: OTHER_SPORT_COLOR)
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

@Composable
private fun AchievementsCard(t: Texts, rows: List<ActivityRow>) {
    val lang = LocalLang.current
    val states = evaluateAchievements(
        rows.map {
            ActivityStat(
                startWallMillis = it.startWallMillis,
                distanceM = it.distanceM,
                elapsedNanos = it.elapsedNanos,
                climbM = it.climbM,
                sport = it.sport,
            )
        },
    )
    val unlockedCount = states.count { it.unlocked }
    Surface(
        color = Palette.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .appearIn(4),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.achievements, color = Palette.text, fontSize = 14.sp)
                Text(
                    "$unlockedCount / ${states.size}",
                    color = if (states.isNotEmpty() && unlockedCount == states.size) {
                        Palette.accent
                    } else {
                        Palette.textSecondary
                    },
                    fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Palette.textSecondary.copy(alpha = 0.18f)),
            ) {
                if (states.isNotEmpty() && unlockedCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(unlockedCount.toFloat() / states.size)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Palette.accent),
                    )
                }
            }
            val order = listOf(
                AchFamily.VOLUME,
                AchFamily.COUNT,
                AchFamily.SPEED,
                AchFamily.CLIMB,
                AchFamily.VARIETY,
                AchFamily.STREAK,
                AchFamily.TIME,
            )
            order.forEach { family ->
                val familyStates = states.filter { it.family == family }
                if (familyStates.isEmpty()) return@forEach
                val familyUnlocked = familyStates.count { it.unlocked }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        extraText(lang, family),
                        color = Palette.textSecondary,
                        fontSize = 11.sp,
                    )
                    Text(
                        "$familyUnlocked/${familyStates.size}",
                        color = Palette.textSecondary.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                    )
                }
                familyStates.chunked(4).forEach { rowStates ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowStates.forEach { st ->
                            AchievementCell(lang, t, st, modifier = Modifier.weight(1f))
                        }
                        repeat(4 - rowStates.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCell(
    lang: dev.toufik.trailmark.app.Lang,
    t: Texts,
    st: AchievementState,
    modifier: Modifier = Modifier,
) {
    val title = achievementTitle(lang, st.id)
    val progress = achievementProgress(t, st)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop",
    )
    Column(
        modifier = modifier.semantics { contentDescription = "$title $progress" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(44.dp).scale(if (st.unlocked) pop else 1f)) {
            val r = size.minDimension / 2f
            if (st.unlocked) {
                drawCircle(Palette.accent, radius = r * 0.92f)
                val p = Path().apply {
                    moveTo(r * 0.56f, r * 1.06f)
                    lineTo(r * 0.90f, r * 1.36f)
                    lineTo(r * 1.46f, r * 0.72f)
                }
                drawPath(
                    p,
                    color = Palette.bg,
                    style = Stroke(
                        width = r * 0.20f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            } else {
                val rr = r * 0.92f
                drawCircle(
                    color = Palette.textSecondary.copy(alpha = 0.30f),
                    radius = rr,
                    style = Stroke(width = r * 0.13f),
                )
                val frac = st.fraction().toFloat().coerceIn(0f, 1f)
                if (frac > 0f) {
                    drawArc(
                        color = Palette.accent.copy(alpha = 0.85f),
                        startAngle = -90f,
                        sweepAngle = 360f * frac,
                        useCenter = false,
                        topLeft = Offset(r - rr, r - rr),
                        size = Size(rr * 2f, rr * 2f),
                        style = Stroke(width = r * 0.13f, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Text(
            title,
            color = if (st.unlocked) Palette.text else Palette.textSecondary,
            fontSize = 10.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            progress,
            color = Palette.textSecondary,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

private fun achievementProgress(t: Texts, st: AchievementState): String = when {
    st.id.startsWith("pace_") -> {
        val best = if (st.raw > 0.0) formatDuration((st.raw * 1_000_000_000.0).toLong()) else "--:--"
        val goal = formatDuration((st.target * 1_000_000_000.0).toLong())
        "$best / $goal"
    }

    st.id.startsWith("climb") ->
        "${formatElevation(st.current)}/${formatElevation(st.target)} ${elevationUnit(t)}"

    st.id.startsWith("dist_") || st.id.startsWith("single_") ->
        "${formatDistance(st.current)}/${formatDistance(st.target)} ${distanceUnit(t)}"

    else -> "${st.current.toInt()}/${st.target.toInt()}"
}

private fun totalsSince(rows: List<ActivityRow>, fromMillis: Long): Totals {
    val out = Totals()
    for (r in rows) {
        // A run still in progress has no final totals; its checkpoint numbers
        // would change the goal ring every second.
        if (r.isOpen) continue
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
        if (r.isOpen) continue
        if (r.startWallMillis >= fromMillis && r.startWallMillis < toMillis) {
            out += r.distanceM
        }
    }
    return out
}

/** Week starts on Monday; computed from the day-of-week offset so it never crosses into a neighbour week. */
internal fun startOfWeek(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    val daysFromMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    c.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    zeroTime(c)
    return c.timeInMillis
}

internal fun startOfMonth(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.DAY_OF_MONTH, 1)
    zeroTime(c)
    return c.timeInMillis
}

/**
 * Start of the week [weeks] before the one [millis] falls in. Calendar-aware, so
 * a DST change neither shifts the boundary nor skips a week.
 */
internal fun weekStartBack(millis: Long, weeks: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = startOfWeek(millis)
    c.add(Calendar.DAY_OF_YEAR, -7 * weeks)
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
