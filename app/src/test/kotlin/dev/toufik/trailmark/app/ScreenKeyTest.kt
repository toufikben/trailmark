package dev.toufik.trailmark.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenKeyTest {
    @Test
    fun everyScreenRoundTrips() {
        val all = listOf(
            Screen.Record,
            Screen.Plan,
            Screen.History,
            Screen.Progress,
            Screen.Settings,
            Screen.Regions,
            Screen.Paywall,
            Screen.Detail(42L),
        )
        all.forEach { assertEquals(it, screenFromKey(screenKey(it))) }
    }

    @Test
    fun unknownOrBrokenKeysFallBackSafely() {
        assertEquals(Screen.Record, screenFromKey("nope"))
        assertEquals(Screen.History, screenFromKey("detail:abc"))
    }
}
