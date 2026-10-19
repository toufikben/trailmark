package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.Texts
import dev.toufik.trailmark.app.WeatherCondition
import dev.toufik.trailmark.app.WeatherNow
import dev.toufik.trailmark.app.formatSpeed
import dev.toufik.trailmark.app.speedUnit
import kotlin.math.roundToInt

/** Weather pill: temperature, wind arrow (points where the wind blows TO), speed and sky. */
@Composable
fun WeatherChip(t: Texts, w: WeatherNow, modifier: Modifier = Modifier) {
    Surface(
        color = Palette.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("${w.tempC.roundToInt()}\u00b0", color = Palette.text, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Canvas(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer { rotationZ = (w.windDeg + 180f) % 360f },
            ) {
                val cx = size.width / 2
                drawLine(
                    color = Palette.accent,
                    start = Offset(cx, size.height),
                    end = Offset(cx, 0f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Palette.accent,
                    start = Offset(cx, 0f),
                    end = Offset(cx - size.width * 0.3f, size.height * 0.36f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Palette.accent,
                    start = Offset(cx, 0f),
                    end = Offset(cx + size.width * 0.3f, size.height * 0.36f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("${formatSpeed(w.windKmh / 3.6)} ${speedUnit(t)}", color = Palette.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(conditionLabel(t, w.condition), color = Palette.textSecondary, fontSize = 12.sp)
        }
    }
}

fun conditionLabel(t: Texts, c: WeatherCondition): String = when (c) {
    WeatherCondition.CLEAR -> t.wClear
    WeatherCondition.PARTLY -> t.wPartly
    WeatherCondition.CLOUDY -> t.wCloudy
    WeatherCondition.FOG -> t.wFog
    WeatherCondition.DRIZZLE -> t.wDrizzle
    WeatherCondition.RAIN -> t.wRain
    WeatherCondition.SNOW -> t.wSnow
    WeatherCondition.STORM -> t.wStorm
}
