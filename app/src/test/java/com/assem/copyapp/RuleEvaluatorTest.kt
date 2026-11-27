package com.assem.copyapp

import com.assem.copyapp.domain.ClockGuard
import com.assem.copyapp.domain.RuleAction
import com.assem.copyapp.domain.RuleEvaluator
import com.assem.copyapp.domain.RuleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RuleEvaluatorTest {

    @Test
    fun active_rules_respect_day_and_window() {
        val guard = ClockGuard(wallClock = { FIXED_WALL }, monotonic = { 0L })
        val rule = RuleSnapshot(
            packageName = "com.example.app",
            action = RuleAction.BLOCK_APP,
            startMin = 0,
            endMin = 1440,
            daysMask = 0x7F
        )
        assertTrue(RuleEvaluator.activeRules(guard, listOf(rule), FIXED_WALL).contains(rule))
    }

    @Test
    fun disabled_rule_is_ignored() {
        val guard = ClockGuard(wallClock = { FIXED_WALL }, monotonic = { 0L })
        val rule = RuleSnapshot(packageName = "com.example.app", enabled = false)
        assertTrue(RuleEvaluator.activeRules(guard, listOf(rule), FIXED_WALL).isEmpty())
    }

    @Test
    fun night_rules_are_derived_without_hardcoded_apps() {
        val rules = RuleEvaluator.deriveNightRules(true, 22 * 60, 23 * 60, 22 * 60 + 30)
        assertEquals(1, rules.size)
        assertTrue(rules.any { it.sectionId == "wind_down" && it.packageName == "*" })

        val bedtime = RuleEvaluator.deriveNightRules(true, 22 * 60, 23 * 60, 23 * 60 + 30)
        assertEquals(1, bedtime.size)
        assertTrue(bedtime.any { it.sectionId == "bedtime" })

        assertTrue(RuleEvaluator.deriveNightRules(false, 22 * 60, 23 * 60, 22 * 60 + 30).isEmpty())
    }

    @Test
    fun priority_orders_block_app_above_allow() {
        assertTrue(
            RuleEvaluator.priority(RuleAction.BLOCK_APP) > RuleEvaluator.priority(RuleAction.ALLOW)
        )
    }

    private companion object {
        val FIXED_WALL: Long = Calendar.getInstance().apply {
            set(2026, 0, 1, 12, 0, 0)
        }.timeInMillis
    }
}
