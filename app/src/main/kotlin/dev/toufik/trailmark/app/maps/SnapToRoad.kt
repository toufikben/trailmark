package dev.toufik.trailmark.app.maps

import android.content.Context
import dev.toufik.trailmark.app.plan.GhRouter
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.displayablePoints

/**
 * Redraws a saved track on the road network from the offline routing graph.
 *
 * Recording keeps the raw GPS trace: that is the honest record of the effort and
 * what every export must contain. What hurts is the *picture* — phone fixes drift
 * a few metres and the drawn line cuts across buildings. This projects the trace
 * onto the closest street for display only, and returns null when no routing graph
 * is installed or too little of the trace matched, so callers fall back to raw.
 */
object SnapToRoad {

    fun points(context: Context, points: List<StoredPoint>): List<StoredPoint>? {
        val live = displayablePoints(points)
        if (live.size < 4) return null
        val snapped = GhRouter.snapToRoad(context, live.map { it.lat to it.lon }) ?: return null
        val (coords, ratio) = snapped
        if (ratio < 0.5) return null
        return coords.mapIndexed { i, p ->
            val src = live.getOrElse(i) { live.last() }
            src.copy(lat = p.first, lon = p.second)
        }
    }
}
