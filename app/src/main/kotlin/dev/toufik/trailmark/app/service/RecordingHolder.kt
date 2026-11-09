package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.recording.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shared recording state between the foreground service and the UI. */
object RecordingHolder {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    fun update(s: RecordingState) {
        _state.value = s
    }

    /**
     * The live ghost gap, written by the screen that draws the rider and read by
     * the service that speaks. Positive means the ghost is ahead.
     */
    private val _ghostDelta = MutableStateFlow<Int?>(null)
    val ghostDelta: StateFlow<Int?> = _ghostDelta

    fun setGhostDelta(deltaM: Int?) {
        if (_ghostDelta.value != deltaM) _ghostDelta.value = deltaM
    }
}
