package com.assem.copyapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.assem.copyapp.domain.ScoreCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as App).container
                val settings = container.repository.settingsFlow().first()
                if (!settings.digestEnabled) return@launch
                val events = container.repository.usageEvents().first()
                val sessions = container.repository.sessions().first()
                val input = ScoreCalculator.ScoreInput(
                    reels = events.count { it.sectionId.equals("reels", true) },
                    shorts = events.count { it.sectionId.equals("shorts", true) },
                    feedScrolls = events.count { it.kind.equals("FEED_SCROLL", true) },
                    breaks = container.repository.breaks().first().size,
                    nightMinutes = sessions.sumOf { it.actualMinutes }.coerceAtMost(120) / 10,
                    budgetBreaches = events.count { it.kind.equals("BUDGET_BREACH", true) },
                    focusMinutes = sessions.sumOf { it.actualMinutes }
                )
                val score = ScoreCalculator.score(input)
                val open = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.digest_channel),
                        NotificationManager.IMPORTANCE_LOW
                    )
                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setContentTitle(context.getString(R.string.digest_title))
                    .setContentText(context.getString(R.string.digest_text, score))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(77, notification)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "daily_digest"
    }
}
