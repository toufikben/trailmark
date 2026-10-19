package dev.toufik.trailmark.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PremiumStateTest {

    private val day = 86_400_000L
    private val start = 1_700_000_000_000L

    @Test
    fun trialCountsDownFromFullLength() {
        val s = PremiumState(start, null)
        assertEquals(TRIAL_DAYS, s.trialDaysLeft(start))
        assertEquals(TRIAL_DAYS, s.trialDaysLeft(start + 1L))
        assertEquals(10, s.trialDaysLeft(start + 5 * day))
        assertEquals(1, s.trialDaysLeft(start + 15 * day - 1))
    }

    @Test
    fun trialExpiresExactlyAtEnd() {
        val s = PremiumState(start, null)
        assertEquals(0, s.trialDaysLeft(start + 15 * day))
        assertEquals(0, s.trialDaysLeft(start + 16 * day))
        assertFalse(s.inTrial(start + 15 * day))
    }

    @Test
    fun clockRollbackCannotExtendTrial() {
        val s = PremiumState(start, null)
        assertEquals(TRIAL_DAYS, s.trialDaysLeft(start - 10 * day))
        assertTrue(s.inTrial(start - 365 * day))
    }

    @Test
    fun purchaseOutlivesTrial() {
        val s = PremiumState(start, PremiumProducts.MONTHLY)
        assertTrue(s.isPro(start + 400 * day))
        assertFalse(PremiumState(start, null).isPro(start + 16 * day))
    }

    @Test
    fun onlyLifetimeIsNotASubscription() {
        assertTrue(PremiumProducts.isSubscription(PremiumProducts.MONTHLY))
        assertTrue(PremiumProducts.isSubscription(PremiumProducts.YEARLY))
        assertFalse(PremiumProducts.isSubscription(PremiumProducts.LIFETIME))
    }
}
