package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as App).container
                container.sessionController.restoreAfterBoot()
                if (container.sessionController.currentSnapshot().active) {
                    ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
