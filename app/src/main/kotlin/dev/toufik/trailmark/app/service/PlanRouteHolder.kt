package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.core.geo.RouteChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Planned route chunks to overlay on the recording map. Set from the planner's
 * "start this route" action and cleared by the user while recording.
 */
object PlanRouteHolder {
    private val _state = MutableStateFlow<List<RouteChunk>>(emptyList())
    val state: StateFlow<List<RouteChunk>> = _state

    fun set(chunks: List<RouteChunk>) {
        _state.value = chunks
    }

    fun clear() {
        _state.value = emptyList()
    }
}
