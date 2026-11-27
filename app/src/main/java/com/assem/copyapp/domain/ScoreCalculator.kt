package com.assem.copyapp.domain

object ScoreCalculator {

    data class ScoreInput(
        val reels: Int = 0,
        val shorts: Int = 0,
        val feedScrolls: Int = 0,
        val breaks: Int = 0,
        val nightMinutes: Int = 0,
        val budgetBreaches: Int = 0,
        val focusMinutes: Int = 0
    )

    fun score(input: ScoreInput): Int {
        var value = 100L
        value -= input.reels.toLong() * 3L
        value -= input.shorts.toLong() * 2L
        value -= input.feedScrolls.toLong()
        value -= input.breaks.toLong() * 4L
        value -= input.nightMinutes.toLong() / 10L
        value -= input.budgetBreaches.toLong() * 8L
        value -= input.focusMinutes.toLong() / 10L
        return value.coerceIn(0L, 100L).toInt()
    }

    fun level(score: Int): String = when {
        score >= 80 -> "mashroo3"
        score >= 60 -> "motawasit"
        score >= 40 -> "khatr"
        else -> "ghair"
    }
}
