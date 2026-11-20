package dev.toufik.trailmark.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The first-run tour reads its copy through [extraText], which silently falls back
 * to the raw key. That fallback is good at runtime and terrible in review, so this
 * test pins every card in every language: a missing string shows up as "w3Body" on
 * screen, and here as a failure.
 */
class WelcomeTextTest {

    private val keys = buildList {
        add("welcomeNext")
        add("welcomeSkip")
        add("w6Cta")
        (1..6).forEach { page ->
            add("w${page}Title")
            add("w${page}Body")
        }
    }

    @Test
    fun everyCardIsTranslatedInEveryLanguage() {
        val missing = mutableListOf<String>()
        Lang.entries.forEach { lang ->
            keys.forEach { key ->
                if (extraText(lang, key) == key) missing += "${lang.code}:$key"
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun noCardReusesAnotherCardsCopy() {
        // A copy-paste slip leaves the same paragraph on two cards; that reads as a
        // bug to the user and hides the missing translation from the test above.
        Lang.entries.forEach { lang ->
            val bodies = (1..6).map { extraText(lang, "w${it}Body") }
            assertEquals(bodies.size, bodies.toSet().size, "duplicate body in ${lang.code}")
            val titles = (1..6).map { extraText(lang, "w${it}Title") }
            assertEquals(titles.size, titles.toSet().size, "duplicate title in ${lang.code}")
        }
    }

    @Test
    fun cardCopyIsSubstantial() {
        // A truncated translation ("Next" left in a body slot) passes the key check
        // but ruins the card; bodies are a paragraph, not a label.
        Lang.entries.forEach { lang ->
            (1..6).forEach { page ->
                val body = extraText(lang, "w${page}Body")
                assertTrue(body.length >= 60, "${lang.code}:w${page}Body too short")
            }
        }
    }
}
