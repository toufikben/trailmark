package dev.toufik.trailmark.app.ui

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Units
import dev.toufik.trailmark.app.formatDuration
import dev.toufik.trailmark.core.geo.haversineMeters
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val MPS_TO_KMH = 3.6
private const val MPS_TO_MPH = 2.2369362920544

/**
 * Full-screen bike-computer style speedometer: one huge number in km/h or mph,
 * plus session max, average, distance and moving time. Independent of the
 * recorder, so it also reads speed while the app is idle.
 */
@Composable
fun SpeedometerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    val granted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var imperial by remember { mutableStateOf(Units.imperial) }
    var speedMps by remember { mutableStateOf<Double?>(null) }
    var maxMps by remember { mutableStateOf(0.0) }
    var distanceM by remember { mutableStateOf(0.0) }
    var movingNanos by remember { mutableStateOf(0L) }
    var accuracyM by remember { mutableStateOf<Double?>(null) }
    var lastFixAt by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val lastLocation = remember { arrayOfNulls<Location>(1) }

    BackHandler(onBack = onBack)

    DisposableEffect(granted, lm) {
        var listener: LocationListener? = null
        if (granted && lm != null) {
            val l = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val prev = lastLocation[0]
                    val dtMs = if (prev != null) location.time - prev.time else 0L
                    // Locations arrive from GPS and network providers; only a real
                    // update interval carries a trustworthy distance/speed sample.
                    val usable = dtMs in 900..30_000
                    val derived = if (usable) {
                        haversineMeters(
                            prev!!.latitude,
                            prev.longitude,
                            location.latitude,
                            location.longitude,
                        ) / (dtMs / 1000.0)
                    } else {
                        null
                    }
                    val v = when {
                        location.hasSpeed() && location.speed >= 0f -> location.speed.toDouble()
                        derived != null && derived.isFinite() -> derived
                        else -> 0.0
                    }
                    if (usable) {
                        val step = haversineMeters(
                            prev!!.latitude,
                            prev.longitude,
                            location.latitude,
                            location.longitude,
                        )
                        val accurate = !location.hasAccuracy() || location.accuracy <= 30f
                        val implied = step / (dtMs / 1000.0)
                        val reported = if (location.hasSpeed() && location.speed >= 0f) {
                            location.speed.toDouble()
                        } else {
                            implied
                        }
                        // A jump far quicker than the receiver's own speed is drift,
                        // and a stationary fix reports almost no speed at all.
                        val plausible = reported >= 0.7 && implied <= reported * 2.5 + 1.0
                        if (accurate && plausible && step in 0.4..180.0) {
                            distanceM += step
                            movingNanos += dtMs * 1_000_000L
                        }
                    }
                    if (v.isFinite() && v in 0.0..70.0) {
                        speedMps = v
                        if (v > maxMps) maxMps = v
                    }
                    if (location.hasAccuracy()) accuracyM = location.accuracy.toDouble()
                    lastFixAt = System.currentTimeMillis()
                    if (prev == null || usable) lastLocation[0] = location
                }
            }
            listener = l
            runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper())
            }
            runCatching {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, l, Looper.getMainLooper())
            }
            val last = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            if (last != null) l.onLocationChanged(last)
        }
        onDispose {
            if (listener != null && lm != null) runCatching { lm.removeUpdates(listener) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val unit = if (imperial) MPS_TO_MPH else MPS_TO_KMH
    val shown = speedMps?.let { it * unit }
    val digits = when {
        shown == null -> "--"
        shown < 10.0 -> String.format(Locale.US, "%.1f", shown)
        else -> shown.roundToInt().toString()
    }
    val avgMps = if (movingNanos > 0L) distanceM / (movingNanos / 1e9) else 0.0
    val status = when {
        !granted -> t.needsLocation
        lastFixAt == 0L -> t.searching
        now - lastFixAt < 6_000L -> t.active
        now - lastFixAt < 20_000L -> t.weak
        else -> t.lost
    }
    val statusColor = when {
        !granted || status == t.lost -> Palette.dangerText
        status == t.weak -> Color(0xFFFFB224)
        else -> Palette.accent
    }

    fun reset() {
        speedMps = null
        maxMps = 0.0
        distanceM = 0.0
        movingNanos = 0L
        lastLocation[0] = null
    }

    Box(modifier = Modifier.fillMaxSize().background(Palette.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(t.back, color = Palette.accent) }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.surface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    UnitChip(
                        label = t.kmh,
                        selected = !imperial,
                        onClick = { imperial = false },
                    )
                    UnitChip(
                        label = t.mph,
                        selected = imperial,
                        onClick = { imperial = true },
                    )
                }
            }

            Spacer(Modifier.weight(0.7f))
            Text(
                text = digits,
                color = Palette.accent,
                fontSize = 118.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (imperial) t.mph else t.kmh,
                color = Palette.textSecondary,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.surface)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusColor),
                )
                Text(status, color = statusColor, fontSize = 12.sp)
                accuracyM?.let { acc ->
                    val shownAcc = if (imperial) acc * 3.2808398950131 else acc
                    Text(
                        "±${String.format(Locale.US, "%.0f", shownAcc)} ${if (imperial) t.feet else t.metre}",
                        color = Palette.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    value = if (maxMps > 0.0) {
                        String.format(Locale.US, "%.1f", maxMps * unit)
                    } else {
                        "--"
                    },
                    label = t.maxSpeed,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    value = if (avgMps > 0.0) String.format(Locale.US, "%.1f", avgMps * unit) else "--",
                    label = "\u00D8",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    value = String.format(
                        Locale.US,
                        "%.2f",
                        if (imperial) distanceM / 1609.344 else distanceM / 1000.0,
                    ),
                    label = if (imperial) t.miles else t.km,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    value = formatDuration(movingNanos),
                    label = t.time,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.accent,
                    contentColor = Palette.onAccent,
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(0.6f).height(46.dp),
            ) {
                Text(t.clear, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Palette.onAccent else Palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Palette.textSecondary, fontSize = 11.sp)
    }
}
