package com.assem.copyapp.domain

import java.util.Calendar

object RuleEvaluator {

    fun activeRules(
        clock: ClockGuard,
        rules: List<RuleSnapshot>,
        nowWall: Long = clock.nowWall()
    ): List<RuleSnapshot> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowWall
        val dayBit = 1 shl (calendar.get(Calendar.DAY_OF_WEEK) - 1)
        val nowMin = clock.minutesOfDay(nowWall)
        return rules.filter { rule ->
            rule.enabled &&
                (rule.daysMask and dayBit) != 0 &&
                (rule.startMin < 0 || rule.endMin < 0 || clock.isWithin(rule.startMin, rule.endMin, nowMin))
        }
    }

    fun deriveNightRules(
        settingsWindDownEnabled: Boolean,
        windDownStartMin: Int,
        bedtimeMin: Int,
        nowMin: Int
    ): List<RuleSnapshot> {
        if (!settingsWindDownEnabled) return emptyList()
        if (windDownStartMin !in 0..1439 || bedtimeMin !in 0..1439 || windDownStartMin == bedtimeMin) {
            return emptyList()
        }

        val rules = mutableListOf<RuleSnapshot>()
        val windDownActive = if (windDownStartMin < bedtimeMin) {
            nowMin >= windDownStartMin && nowMin < bedtimeMin
        } else {
            nowMin >= windDownStartMin || nowMin < bedtimeMin
        }
        val bedtimeActive = if (bedtimeMin > windDownStartMin) {
            nowMin >= bedtimeMin || nowMin < windDownStartMin
        } else {
            nowMin >= bedtimeMin && nowMin < windDownStartMin
        }
        if (windDownActive) {
            rules += RuleSnapshot(
                packageName = "*",
                sectionId = "wind_down",
                action = RuleAction.BLOCK_SECTION,
                startMin = windDownStartMin,
                endMin = bedtimeMin
            )
        }
        if (bedtimeActive) {
            rules += RuleSnapshot(
                packageName = "*",
                sectionId = "bedtime",
                action = RuleAction.BLOCK_APP,
                startMin = bedtimeMin,
                endMin = windDownStartMin
            )
        }
        return rules
    }

    fun priority(action: RuleAction): Int = when (action) {
        RuleAction.ALLOW -> 0
        RuleAction.COUNT_ONLY -> 1
        RuleAction.GATE -> 2
        RuleAction.BLOCK_SECTION -> 3
        RuleAction.BLOCK_APP -> 4
    }
}
