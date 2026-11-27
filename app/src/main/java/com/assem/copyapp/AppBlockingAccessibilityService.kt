package com.assem.copyapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.assem.copyapp.domain.BlockReason
import com.assem.copyapp.domain.Decision
import com.assem.copyapp.domain.GateReason
import com.assem.copyapp.domain.NodeInfoSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppBlockingAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false
    private var lastBlocked: String? = null

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BlockingActivity.ACTION_CLOSE) lastBlocked = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 50
        }
        registerClose()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val container = (applicationContext as App).container
        val root = rootInActiveWindow
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: root?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: return
        val className = event.className?.toString()?.takeIf { it.isNotBlank() }
            ?: root?.className?.toString().orEmpty()
        val snapshot = root?.let { snapshotOf(it) }

        scope.launch {
            container.lastNode.value = snapshot
            val decision = container.enforcementEngine.decide(pkg, className, snapshot)
            when (decision) {
                is Decision.Allow -> Unit
                is Decision.BlockApp -> withContext(Dispatchers.Main) {
                    blockApp(decision.packageName, decision.reason)
                }
                is Decision.BlockSection -> withContext(Dispatchers.Main) {
                    blockApp(decision.packageName, decision.reason)
                }
                is Decision.Gate -> withContext(Dispatchers.Main) {
                    blockApp(decision.packageName.ifBlank { pkg }, decision.reason.toBlockReason())
                }
                is Decision.CountOnly -> {
                    container.sessionController.addUsedMinutes(decision.packageName, 1)
                }
            }
        }
    }

    override fun onInterrupt() {
        lastBlocked = null
    }

    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(closeReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun blockApp(packageName: String, reason: BlockReason) {
        if (packageName == this.packageName) return
        if (rootInActiveWindow?.packageName?.toString() == this.packageName) return
        if (packageName == lastBlocked) return

        lastBlocked = packageName
        killApp(packageName)
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, BlockingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(BlockingActivity.EXTRA_APP_LABEL, labelFor(packageName))
            putExtra(BlockingActivity.EXTRA_REASON, reason.name)
        }
        runCatching { startActivity(intent) }.onFailure { lastBlocked = null }
    }

    private fun killApp(packageName: String) {
        runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        }
    }

    private fun labelFor(packageName: String): String = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    }.getOrDefault(packageName)

    private fun snapshotOf(node: AccessibilityNodeInfo): NodeInfoSnapshot {
        val texts = mutableListOf<String>()
        var viewId: String? = null
        var contentDesc: String? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(node)
        var visited = 0
        while (stack.isNotEmpty() && visited < 80) {
            val current = stack.removeFirst()
            visited++
            current.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            if (viewId == null) current.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { viewId = it }
            if (contentDesc == null) {
                current.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { contentDesc = it }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return NodeInfoSnapshot(
            packageName = node.packageName?.toString(),
            className = node.className?.toString(),
            text = texts.firstOrNull(),
            contentDescription = contentDesc,
            viewIdResourceName = viewId,
            childTexts = texts.take(20)
        )
    }

    private fun registerClose() {
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

    private fun GateReason.toBlockReason(): BlockReason = when (this) {
        GateReason.BUDGET -> BlockReason.BUDGET
        GateReason.BREAK -> BlockReason.SESSION_APP
        GateReason.PAUSE -> BlockReason.SESSION_APP
        GateReason.FRIEND_KEY -> BlockReason.SESSION_APP
        GateReason.EMERGENCY -> BlockReason.SESSION_APP
    }
}
