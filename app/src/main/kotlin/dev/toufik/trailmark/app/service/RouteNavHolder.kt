package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.core.geo.TurnKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the recording screen should show for the followed planned route. */
data class RouteNav(
    val nextTurn: TurnKind?,
    val turnInM: Double,
    val remainingM: Double,
    val offRoute: Boolean,
)

/**
 * Live navigation state for the planned route, updated by the recording
 * service and consumed by the map overlay. Null when no route is followed.
 */
object RouteNavHolder {
    private val _state = MutableStateFlow<RouteNav?>(null)
    val state: StateFlow<RouteNav?> = _state

    fun update(nav: RouteNav?) {
        _state.value = nav
    }
}
