package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.core.geo.GeoProjection
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType

/** Draws a recorded route (rejected points excluded) with start/end markers. */
@Composable
fun RouteCanvas(points: List<StoredPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val route = points.filter { it.type != WaypointType.REJECTED }
        if (route.isEmpty()) return@Canvas
        val proj = GeoProjection.of(
            route.map { it.lat },
            route.map { it.lon },
            size.width.toDouble(),
            size.height.toDouble(),
        ) ?: return@Canvas

        if (route.size >= 2) {
            val path = Path()
            route.forEachIndexed { i, p ->
                val x = proj.x(p.lon).toFloat()
                val y = proj.y(p.lat).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = Palette.accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        val start = route.first()
        drawCircle(
            color = Palette.accent,
            radius = 8f,
            center = Offset(proj.x(start.lon).toFloat(), proj.y(start.lat).toFloat()),
        )
        val end = route.last()
        drawCircle(
            color = Palette.text,
            radius = 8f,
            center = Offset(proj.x(end.lon).toFloat(), proj.y(end.lat).toFloat()),
        )
    }
}
