package com.assem.copyapp

import android.content.Context
import android.content.Intent
import com.assem.copyapp.data.AppRepository
import com.assem.copyapp.data.BreakEntity
import com.assem.copyapp.data.SessionEntity
import com.assem.copyapp.data.SessionState
import com.assem.copyapp.domain.ClockGuard
import com.assem.copyapp.domain.EnforcementEngine
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

class SessionController(
    private val context: Context,
    private val repository: AppRepository,
    private val clockGuard: ClockGuard,
    private val enforcement: EnforcementEngine
) {

    sealed class StartResult {
        data object Success : StartResult()
        data object AlreadyRunning : StartResult()
        data object InvalidDuration : StartResult()
        data object NoAppsSelected : StartResult()
        data object Locked : StartResult()
    }

    suspend fun start(
        durationMinutes: Int,
        packages: Set<String>,
        mode: SessionMode,
        strict: Boolean,
        profileId: Long = 0L
    ): StartResult {
        if (repository.sessionFlow().first().active) return StartResult.AlreadyRunning
        if (durationMinutes <= 0) return StartResult.InvalidDuration
        if (!strict && packages.isEmpty()) return StartResult.NoAppsSelected

        val clock = clockGuard.makeClock(durationMinutes)
        val entity = SessionEntity(
            startWall = clock.startWall,
            endWall = clock.endWall,
            startMono = clock.startMono,
            mode = mode.name,
            blockedCsv = packages.joinToString(","),
            strict = strict,
            plannedMinutes = durationMinutes,
            profileId = profileId
        )
        val id = repository.addSession(entity)
        repository.startSession(
            SessionState(
                active = true,
                startWall = clock.startWall,
                endWall = clock.endWall,
                startMono = clock.startMono,
                mode = mode,
                blocked = packages,
                strict = strict,
                profileId = profileId,
                wallTrusted = true,
                sessionId = id
            )
        )
        context.startForegroundService(Intent(context, TimerService::class.java))
        return StartResult.Success
    }

    suspend fun end(reason: String, fromUser: Boolean): Boolean {
        val state = repository.sessionFlow().first()
        if (!state.active) return true
        if (state.mode == SessionMode.HARD && reason !in setOf("TIME", "FRIEND_KEY", "SYSTEM")) {
            return false
        }

        val elapsed = (clockGuard.nowMono() - state.startMono).coerceAtLeast(0)
        val actualMinutes = (elapsed / 60_000L).toInt()
        if (state.sessionId != 0L) {
            repository.markSessionCompleted(state.sessionId, actualMinutes)
        }
        if (fromUser && reason != "TIME") {
            repository.addBreak(
                BreakEntity(
                    sessionId = state.sessionId,
                    atWall = clockGuard.nowWall(),
                    reason = reason,
                    minutesCost = 0
                )
            )
        }
        repository.clearSession()
        context.sendBroadcast(Intent(BlockingActivity.ACTION_CLOSE))
        return true
    }

    suspend fun pauseFor(seconds: Int) {
        repository.updateSession { it.copy(pauseUntilMono = clockGuard.nowMono() + seconds * 1000L) }
    }

    suspend fun restoreAfterBoot() {
        val state = repository.sessionFlow().first()
        if (!state.active) return
        val nowWall = clockGuard.nowWall()
        if (state.endWall <= nowWall) {
            end("TIME", fromUser = false)
            return
        }
        val remainingWall = state.endWall - nowWall
        repository.updateSession {
            it.copy(
                startMono = clockGuard.nowMono() - remainingWall,
                wallTrusted = false
            )
        }
    }

    suspend fun friendKeyMatches(code: String): Boolean {
        val stored = repository.settingsFlow().first().friendKeyHash
        if (stored.isBlank()) return false
        return sha256(code.trim()) == stored
    }

    suspend fun addUsedMinutes(packageName: String, minutes: Int) {
        if (minutes <= 0) return
        val now = clockGuard.nowWall()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))
        repository.addUsedMinutes(date, packageName, minutes)
    }

    suspend fun currentSnapshot(): SessionState = repository.sessionFlow().first()

    suspend fun remainingMillis(state: SessionState): Long {
        if (!state.active) return 0L
        return clockGuard.remaining(
            ClockGuard.SessionClock(state.startWall, state.endWall, state.startMono)
        )
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
