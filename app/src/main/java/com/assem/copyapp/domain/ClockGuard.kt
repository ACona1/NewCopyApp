package com.assem.copyapp.domain

import java.util.Calendar
import kotlin.math.abs

class ClockGuard(
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val monotonic: () -> Long = android.os.SystemClock::elapsedRealtime
) {
    data class SessionClock(val startWall: Long, val endWall: Long, val startMono: Long)

    fun nowWall(): Long = wallClock()

    fun nowMono(): Long = monotonic()

    fun start(startWall: Long = nowWall(), startMono: Long = nowMono()): SessionClock =
        SessionClock(startWall = startWall, endWall = 0L, startMono = startMono)

    fun makeClock(durationMinutes: Int): SessionClock {
        val startWall = nowWall()
        val durationMs = durationMinutes.coerceAtLeast(0).toLong() * 60_000L
        return SessionClock(startWall, startWall + durationMs, nowMono())
    }

    fun remaining(clock: SessionClock): Long {
        val planned = (clock.endWall - clock.startWall).coerceAtLeast(0L)
        // المعيار هو الوقت الرتيب؛ الوقت العادي احتياطي فقط
        val monoElapsed = nowMono() - clock.startMono
        val elapsed = if (monoElapsed > 0L) monoElapsed else (nowWall() - clock.startWall).coerceAtLeast(0L)
        return (planned - elapsed).coerceAtLeast(0L)
    }

    fun isWallTrusted(clock: SessionClock): Boolean {
        val wallDelta = nowWall() - clock.startWall
        val monoDelta = nowMono() - clock.startMono
        return abs(wallDelta - monoDelta) <= 60_000L
    }

    fun minutesOfDay(wall: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = wall
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    fun isWithin(startMin: Int, endMin: Int, nowMin: Int): Boolean {
        val start = normalize(startMin)
        val end = normalize(endMin)
        val now = normalize(nowMin)
        return when {
            start == end -> true
            start < end -> now in start until end
            else -> now >= start || now < end // ليلاً عبر منتصف الليل
        }
    }

    fun remainingMinutes(startMin: Int, endMin: Int, nowMin: Int): Int {
        val start = normalize(startMin)
        val end = normalize(endMin)
        val now = normalize(nowMin)
        return when {
            start == end -> 1440
            isWithin(start, end, now) ->
                if (start < end) end - now
                else if (now >= start) 1440 - now + end
                else end - now // بعد منتصف الليل وقبل نهاية النافذة الليلية
            now < start -> start - now
            else -> 1440 - now + start
        }
    }

    private fun normalize(minute: Int): Int = ((minute % 1440) + 1440) % 1440
}
