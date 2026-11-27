package com.assem.copyapp

import com.assem.copyapp.domain.BudgetTracker
import com.assem.copyapp.domain.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun score_is_clamped_and_focus_reduces_it() {
        val bad = ScoreCalculator.score(
            ScoreCalculator.ScoreInput(reels = 20, shorts = 20, feedScrolls = 50, breaks = 5, budgetBreaches = 5)
        )
        val good = ScoreCalculator.score(
            ScoreCalculator.ScoreInput(reels = 0, focusMinutes = 240)
        )
        assertTrue(bad in 0..100)
        assertTrue(good in 0..100)
        assertTrue(bad > good)
    }

    @Test
    fun budget_limit_zero_means_unlimited() {
        assertFalse(BudgetTracker.isExceeded("com.x", mapOf("com.x" to 0), mapOf("com.x" to 999)))
        assertEquals(-1, BudgetTracker.remaining("com.x", mapOf("com.x" to 0), emptyMap()))
        assertTrue(BudgetTracker.isExceeded("com.x", mapOf("com.x" to 30), mapOf("com.x" to 31)))
    }

    @Test
    fun levels_are_plain_keys() {
        assertTrue(
            setOf("mashroo3", "motawasit", "khatr", "ghair").contains(
                ScoreCalculator.level(10)
            )
        )
    }
}
