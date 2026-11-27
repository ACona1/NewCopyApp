package com.assem.copyapp

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class AppBlockingAccessibilityService : AccessibilityService() {

    private lateinit var session: SessionManager
    private val handler = Handler(Looper.getMainLooper())
    private var lockActive = false
    private var lastBlockedPackage: String? = null
    private var receiverRegistered = false

    private val checker = object : Runnable {
        override fun run() {
            try {
                if (!session.isActive()) {
                    if (lockActive) {
                        lockActive = false
                        lastBlockedPackage = null
                        sendBroadcast(Intent(BlockingActivity.ACTION_CLOSE))
                    }
                } else {
                    val foreground = rootInActiveWindow?.packageName?.toString()
                    if (!foreground.isNullOrBlank()) evaluate(foreground)
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 800L)
        }
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BlockingActivity.ACTION_CLOSE) {
                lockActive = false
                lastBlockedPackage = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
        registerCloseReceiver()
        handler.post(checker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        if (
            pkg == "com.android.systemui" &&
            className.contains("Recents", ignoreCase = true) &&
            session.isActive() &&
            lastBlockedPackage != null
        ) {
            val blocked = lastBlockedPackage ?: return
            killApp(blocked)
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({ showBlockingUi(blocked) }, 200L)
            return
        }

        evaluate(pkg)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (
            lockActive &&
            event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_BACK ||
                event.keyCode == KeyEvent.KEYCODE_HOME ||
                event.keyCode == KeyEvent.KEYCODE_APP_SWITCH)
        ) {
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        lockActive = false
    }

    override fun onDestroy() {
        handler.removeCallbacks(checker)
        if (receiverRegistered) {
            try {
                unregisterReceiver(closeReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun evaluate(packageName: String) {
        if (packageName == this.packageName) return
        if (session.isBlocked(packageName)) showBlockingUi(packageName)
    }

    private fun showBlockingUi(packageName: String) {
        if (rootInActiveWindow?.packageName?.toString() == this.packageName) return

        lockActive = true
        lastBlockedPackage = packageName
        killApp(packageName)

        val intent = Intent(this, BlockingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(BlockingActivity.EXTRA_APP_LABEL, labelFor(packageName))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            lockActive = false
        }
    }

    private fun killApp(packageName: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            for (task in activityManager.appTasks) {
                try {
                    val taskPackage = task.taskInfo?.topActivity?.packageName
                        ?: task.taskInfo?.baseIntent?.component?.packageName
                    if (taskPackage == packageName) task.finishAndRemoveTask()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun labelFor(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        info.loadLabel(packageManager).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun registerCloseReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(BlockingActivity.ACTION_CLOSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(closeReceiver, filter)
        }
        receiverRegistered = true
    }
}
