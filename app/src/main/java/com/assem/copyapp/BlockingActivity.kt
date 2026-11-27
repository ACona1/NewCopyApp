package com.assem.copyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.assem.copyapp.domain.BlockReason
import com.assem.copyapp.domain.SessionMode
import com.assem.copyapp.ui.BlockingScreen
import com.assem.copyapp.ui.NewCopyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockingActivity : ComponentActivity() {

    private val remainingFlow = MutableStateFlow(0L)
    private val totalFlow = MutableStateFlow(1L)
    private val modeFlow = MutableStateFlow(SessionMode.SOFT)
    private val frictionFlow = MutableStateFlow(5)
    private var receiverRegistered = false

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CLOSE) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (applicationContext as App).container
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: getString(R.string.app_name)
        val reason = runCatching {
            BlockReason.valueOf(intent.getStringExtra(EXTRA_REASON) ?: BlockReason.SESSION_APP.name)
        }.getOrDefault(BlockReason.SESSION_APP)

        registerClose()

        lifecycleScope.launch(Dispatchers.IO) {
            val state = container.repository.sessionFlow().first()
            modeFlow.value = state.mode
            totalFlow.value = (state.endWall - state.startWall).coerceAtLeast(1L)
            frictionFlow.value = container.repository.settingsFlow().first().pauseRequiredSeconds
            if (!state.active) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }
            while (!isFinishing) {
                remainingFlow.value = container.sessionController.remainingMillis(state)
                delay(1000L)
            }
        }

        setContent {
            val remaining by remainingFlow.collectAsState()
            val mode by modeFlow.collectAsState()
            val friction by frictionFlow.collectAsState()
            val total by totalFlow.collectAsState()
            NewCopyTheme {
                BlockingScreen(
                    appLabel = appLabel,
                    reason = reason,
                    mode = mode,
                    remaining = remaining,
                    totalPlanned = total,
                    frictionSeconds = friction,
                    onHome = {
                        startActivity(Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                    onEmergency = {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onSoftEnd = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            container.sessionController.end("SOFT_FRICTION", fromUser = true)
                            withContext(Dispatchers.Main) { finish() }
                        }
                    },
                    onFriendKey = { code ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val ok = container.sessionController.friendKeyMatches(code)
                            if (ok) {
                                container.sessionController.end("FRIEND_KEY", fromUser = true)
                            }
                            withContext(Dispatchers.Main) {
                                if (ok) {
                                    finish()
                                } else {
                                    Toast.makeText(this@BlockingActivity, R.string.friend_key_wrong, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onIllegalExit = {
                        Toast.makeText(this, R.string.block_exit_not_allowed, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(closeReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
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

    companion object {
        const val ACTION_CLOSE = "com.assem.copyapp.CLOSE_BLOCKER"
        const val EXTRA_APP_LABEL = "com.assem.copyapp.EXTRA_APP_LABEL"
        const val EXTRA_REASON = "com.assem.copyapp.EXTRA_REASON"
    }
}
