package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.app.WeatherNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Latest live weather of the running session; the service persists it when recording stops. */
object WeatherHolder {
    private val _state = MutableStateFlow<WeatherNow?>(null)
    val state: StateFlow<WeatherNow?> = _state

    fun update(value: WeatherNow?) {
        _state.value = value
    }
}
