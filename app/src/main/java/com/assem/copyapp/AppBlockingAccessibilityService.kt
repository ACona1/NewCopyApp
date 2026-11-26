package com.assem.copyapp

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class AppBlockingAccessibilityService : AccessibilityService() {

    private lateinit var session: SessionManager
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return

        if (session.isBlocked(pkg)) {
            showOverlay(pkg)
        } else {
            hideOverlay()
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay(packageName: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView != null && overlayPackage == packageName) return
        hideOverlay()
        overlayPackage = packageName

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 18, 28))
        }

        val title = TextView(this).apply {
            text = "التطبيق محظور أثناء جلسة التركيز"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val detail = TextView(this).apply {
            text = packageName
            textSize = 15f
            setTextColor(Color.rgb(180, 190, 210))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }

        val homeButton = Button(this).apply {
            text = "العودة للشاشة الرئيسية"
            setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME) }
        }

        val emergencyButton = Button(this).apply {
            text = "اتصال طوارئ"
            setOnClickListener {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ContextCompat.startActivity(this@AppBlockingAccessibilityService, intent, null)
            }
        }

        val stopButton = Button(this).apply {
            text = "إنهاء جلسة التركيز"
            setOnClickListener {
                session.stop()
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
                Toast.makeText(
                    this@AppBlockingAccessibilityService,
                    "تم إنهاء الجلسة",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(title)
            addView(detail)
            addView(homeButton)
            addView(emergencyButton)
            addView(stopButton)
        }

        root.addView(
            box,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager?.addView(root, params)
            overlayView = root
        } catch (e: Exception) {
            overlayView = null
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        overlayPackage = null
    }
}
