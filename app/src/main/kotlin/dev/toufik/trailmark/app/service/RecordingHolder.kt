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
}
