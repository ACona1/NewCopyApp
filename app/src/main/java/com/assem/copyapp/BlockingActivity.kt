package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class BlockingActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var remainingText: TextView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!session.isActive()) {
                finish()
                return
            }
            updateTime()
            handler.postDelayed(this, 1000L)
        }
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CLOSE) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        if (!session.isActive()) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: getString(R.string.app_name)
        setContentView(buildUi(appLabel))
        registerClose()
        updateTime()
    }

    override fun onResume() {
        super.onResume()
        if (!session.isActive()) {
            finish()
            return
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "لا يمكن الخروج أثناء جلسة التركيز", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (receiverRegistered) {
            try {
                unregisterReceiver(closeReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun buildUi(appLabel: String): LinearLayout {
        val icon = TextView(this).apply {
            text = "🔒"
            textSize = 52f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }

        val title = TextView(this).apply {
            text = getString(R.string.blocking_title)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@BlockingActivity, R.color.text))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }

        val appName = TextView(this).apply {
            text = appLabel
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@BlockingActivity, R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }

        val message = TextView(this).apply {
            text = "جلسة التركيز نشطة.\nتم إغلاق التطبيق في الخلفية، ولا يمكن استخدامه حتى انتهاء الوقت المحدد."
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@BlockingActivity, R.color.muted))
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        remainingText = TextView(this).apply {
            textSize = 32f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@BlockingActivity, R.color.text))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), dp(24)) }
        }

        val homeButton = actionButton("العودة للشاشة الرئيسية", R.color.primary) {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        val emergencyButton = actionButton("اتصال طوارئ", R.color.surface_2) {
            startActivity(
                Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        val stopButton = actionButton("إنهاء جلسة التركيز", R.color.danger) {
            session.stop()
            finish()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@BlockingActivity, R.color.bg))
            setPadding(dp(28), dp(36), dp(28), dp(36))
            addView(icon)
            addView(title)
            addView(appName)
            addView(message)
            addView(remainingText)
            addView(progressBar)
            addView(homeButton)
            addView(emergencyButton)
            addView(stopButton)
        }
    }

    private fun actionButton(text: String, colorRes: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@BlockingActivity, R.color.text))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@BlockingActivity, colorRes))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
            setOnClickListener { onClick() }
        }

    private fun updateTime() {
        val totalSeconds = (session.remainingMillis() / 1000L).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        remainingText.text = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
        progressBar.progress = session.progressPercent()
    }

    private fun registerClose() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_CLOSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(closeReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_CLOSE = "com.assem.copyapp.CLOSE_BLOCKER"
        const val EXTRA_APP_LABEL = "com.assem.copyapp.EXTRA_APP_LABEL"
    }
}
