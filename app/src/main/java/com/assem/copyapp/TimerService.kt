package com.assem.copyapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.assem.copyapp.data.SessionState
import com.assem.copyapp.domain.ClockGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                getString(R.string.notification_active),
                0L,
                false,
                System.currentTimeMillis()
            )
        )
        scope.launch {
            while (true) {
                val controller = (applicationContext as App).container.sessionController
                val state = controller.currentSnapshot()
                if (!state.active) break
                val clock = (applicationContext as App).container.clockGuard
                val remaining = clock.remaining(
                    ClockGuard.SessionClock(state.startWall, state.endWall, state.startMono)
                )
                if (remaining <= 0) {
                    controller.end("TIME", fromUser = false)
                    break
                }
                withContext(Dispatchers.Main) { showNotification(state, remaining) }
                delay(1000L)
            }
            withContext(Dispatchers.Main) {
                sendBroadcast(Intent(BlockingActivity.ACTION_CLOSE))
                notifyDone()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification(state: SessionState, remaining: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(null, remaining, state.mode.name == "HARD", state.endWall))
    }

    private fun buildNotification(
        text: String?,
        remaining: Long,
        hard: Boolean = false,
        whenMillis: Long = 0L
    ): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = text ?: getString(R.string.notification_remaining, formatTime(remaining))
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
        if (whenMillis > 0L) builder.setWhen(whenMillis)
        if (hard) {
            builder.setSubText(getString(R.string.hard_mode_short))
        } else {
            val endIntent = Intent(this, SessionActionReceiver::class.java).apply {
                action = SessionActionReceiver.ACTION_END_SOFT
            }
            val endPending = PendingIntent.getBroadcast(
                this,
                2,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.end_session), endPending)
        }
        return builder.build()
    }

    private fun notifyDone() {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(getString(R.string.notification_done_title))
            .setContentText(getString(R.string.notification_done_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(DONE_ID, notification)
    }

    private fun formatTime(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0)
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            total / 3600,
            (total % 3600) / 60,
            total % 60
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "focus_session"
        private const val NOTIFICATION_ID = 1001
        private const val DONE_ID = 1002
    }
}
