package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = SessionManager(context)
        if (session.isActive()) {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        }
    }
}
