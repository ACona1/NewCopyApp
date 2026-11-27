package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END_SOFT) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val controller = (context.applicationContext as App).container.sessionController
                controller.end("SOFT_FRICTION", fromUser = true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_END_SOFT = "com.assem.copyapp.END_SOFT"
    }
}
