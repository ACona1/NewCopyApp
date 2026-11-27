package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as App).container
                val profile = container.repository.profiles().first().firstOrNull() ?: return@launch
                if (profile.scheduleStart < 0 || profile.scheduleEnd <= profile.scheduleStart) return@launch
                val now = container.clockGuard.minutesOfDay(container.clockGuard.nowWall())
                if (!container.clockGuard.isWithin(profile.scheduleStart, profile.scheduleEnd, now)) return@launch
                val minutes = container.clockGuard.remainingMinutes(profile.scheduleStart, profile.scheduleEnd, now)
                if (minutes <= 0) return@launch

                val mode = runCatching { SessionMode.valueOf(profile.mode) }
                    .getOrDefault(SessionMode.SOFT)
                val packages = profile.blockedCsv.split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val result = container.sessionController.start(
                    minutes,
                    packages,
                    mode,
                    profile.strict,
                    profile.id
                )
                if (result is SessionController.StartResult.Success) {
                    ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
