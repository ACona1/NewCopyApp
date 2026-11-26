package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = SessionManager(context)
        val (startMin, endMin) = session.schedule()
        if (startMin < 0 || endMin <= startMin) return

        val now = Calendar.getInstance()
        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (currentMin < startMin || currentMin >= endMin) return

        val result = session.start(endMin - currentMin, session.blockedPackages())
        if (result is SessionManager.StartResult.Success) {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        }
    }
}
