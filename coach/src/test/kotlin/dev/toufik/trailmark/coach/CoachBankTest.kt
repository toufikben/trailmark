package dev.toufik.trailmark.coach

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachBankTest {
    private val en = COACH_BANKS.getValue(Locale.EN)

    @Test
    fun everyLanguageCoversEveryEnglishKey() {
        val missing = mutableListOf<String>()
        for (locale in Locale.entries) {
            val bank = COACH_BANKS[locale]
            if (bank == null) {
                missing += "${locale.name}: whole bank"
                continue
            }
            for (key in en.keys) {
                if (bank[key].isNullOrEmpty()) missing += "${locale.name}: $key"
            }
        }
        assertTrue(missing.isEmpty(), "missing coach lines: $missing")
    }

    @Test
    fun everyLineKeepsThePlaceholdersItsEventFills() {
        val problems = mutableListOf<String>()
        for (locale in Locale.entries) {
            val bank = COACH_BANKS.getValue(locale)
            for ((key, lines) in bank) {
                val expected = placeholders(en.getValue(key).first())
                for (line in lines) {
                    if (placeholders(line) != expected) {
                        problems += "${locale.name}/$key: '${placeholders(line)}' != '$expected'"
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "placeholder drift: $problems")
    }

    @Test
    fun messagesAreFilledInForEveryLocale() {
        val events = listOf(
            CoachEvent.PreviousBest(5000.0, 1500L, 5000.0),
            CoachEvent.Ahead(12L),
            CoachEvent.Behind(12L),
            CoachEvent.GhostAhead(30),
            CoachEvent.GhostBehind(30),
            CoachEvent.Overtook(10),
            CoachEvent.GotOvertaken(10),
            CoachEvent.Milestone(3),
            CoachEvent.SegmentStart("Hill"),
            CoachEvent.SegmentFinish("Hill", 300L),
            CoachEvent.SegmentRecord("Hill", 300L, 15L),
            CoachEvent.Turn(TurnDirection.LEFT, 80),
            CoachEvent.Turn(TurnDirection.SHARP_RIGHT, 40),
            CoachEvent.OffRoute,
            CoachEvent.Finish(5000.0, 1500L, true),
        )
        for (locale in Locale.entries) {
            for (event in events) {
                val text = Coach.message(event, locale)
                assertTrue(text.isNotBlank(), "${locale.name} blank for $event")
                assertTrue(!text.contains("{"), "${locale.name} left a placeholder for $event: $text")
            }
        }
    }

    @Test
    fun languageCodesMatchTheUiLanguages() {
        val codes = Locale.entries.map { it.tag }
        assertEquals(
            listOf("ar", "en", "fr", "es", "pt", "de", "it", "nl", "pl", "ru", "tr", "id", "hi", "ja", "ko", "zh"),
            codes,
        )
    }

    private fun placeholders(text: String): Set<String> =
        Regex("\\{(\\w+)}").findAll(text).map { it.groupValues[1] }.toSet()
}
