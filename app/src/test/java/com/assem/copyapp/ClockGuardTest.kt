package com.assem.copyapp

import com.assem.copyapp.domain.ClockGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockGuardTest {

    @Test
    fun remaining_uses_monotonic_clock() {
        var wall = 1_000_000L
        var mono = 5_000L
        val guard = ClockGuard(wallClock = { wall }, monotonic = { mono })
        val clock = guard.makeClock(30)

        assertEquals(30 * 60_000L, guard.remaining(clock))

        mono += 10 * 60_000L
        wall += 100 * 60_000L // محاولة تزوير الساعة للإمام
        assertEquals(20 * 60_000L, guard.remaining(clock))
        assertFalse(guard.isWallTrusted(clock))
    }

    @Test
    fun remaining_never_negative() {
        var mono = 1L
        val guard = ClockGuard(wallClock = { 0L }, monotonic = { mono })
        val clock = guard.makeClock(1)
        mono += 500_000L
        assertEquals(0L, guard.remaining(clock))
    }

    @Test
    fun overnight_window_is_supported() {
        val guard = ClockGuard(wallClock = { 0L }, monotonic = { 0L })
        assertTrue(guard.isWithin(23 * 60, 6 * 60, 23 * 60 + 30))
        assertTrue(guard.isWithin(23 * 60, 6 * 60, 2 * 60))
        assertFalse(guard.isWithin(23 * 60, 6 * 60, 12 * 60))
        assertEquals(30, guard.remainingMinutes(23 * 60, 6 * 60, 22 * 60 + 30))
    }

    @Test
    fun wall_trust_allows_small_drift() {
        var wall = 10_000L
        var mono = 1_000L
        val guard = ClockGuard(wallClock = { wall }, monotonic = { mono })
        val clock = guard.makeClock(10)
        wall += 1_000L
        mono += 1_000L
        assertTrue(guard.isWallTrusted(clock))
    }
}
