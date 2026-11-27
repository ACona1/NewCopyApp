package com.assem.copyapp

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private data class AppItem(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var session: SessionManager
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var permissionText: TextView
    private lateinit var scheduleText: TextView
    private lateinit var usageText: TextView
    private lateinit var deviceText: TextView
    private lateinit var statsText: TextView
    private lateinit var strictCheck: CheckBox
    private lateinit var appsBox: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var durationInput: EditText

    private val appChecks = LinkedHashMap<String, CheckBox>()
    private var allApps: List<AppItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        setContentView(buildUi())
        loadInstalledApps()
        renderApps("")
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.text))
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(18)) })

        val dash = card(getString(R.string.status_card))
        timerText = TextView(this).apply {
            text = getString(R.string.no_active_session)
            textSize = 30f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(color(R.color.text))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        statusText = body("")
        statusText.setPadding(0, dp(12), 0, dp(8))
        dash.addView(timerText)
        dash.addView(progressBar)
        dash.addView(statusText)
        dash.addView(
            buttonRow(
                actionButton(getString(R.string.start_focus), R.color.primary) { startFocusSession() },
                actionButton(getString(R.string.end_session), R.color.surface_2) { stopSession() }
            )
        )
        strictCheck = CheckBox(this).apply {
            text = getString(R.string.strict_mode_label)
            textSize = 13f
            setTextColor(color(R.color.muted))
            buttonTintList = ColorStateList.valueOf(color(R.color.primary))
            isChecked = session.strictMode
            setOnClickListener {
                session.setStrictMode(isChecked)
                toast(getString(if (isChecked) R.string.strict_enabled else R.string.strict_disabled))
            }
        }
        dash.addView(
            strictCheck,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
        )
        root.addView(dash)

        val permCard = card(getString(R.string.permissions_card))
        permissionText = body("—")
        permCard.addView(permissionText)
        val reviewButton = actionButton(getString(R.string.review_permissions), R.color.surface_2) {
            requestAllPermissions()
        }
        reviewButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(10), 0, 0) }
        permCard.addView(reviewButton)
        root.addView(permCard)

        val appsCard = card(getString(R.string.blocked_apps_card))
        searchInput = EditText(this).apply {
            hint = getString(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(color(R.color.text))
            setHintTextColor(color(R.color.muted))
            background = rounded(R.color.surface_2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        appsCard.addView(
            searchInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        )
        appsCard.addView(
            buttonRow(
                actionButton(getString(R.string.select_all), R.color.surface_2) {
                    appChecks.values.forEach { it.isChecked = true }
                },
                actionButton(getString(R.string.clear_all), R.color.surface_2) {
                    appChecks.values.forEach { it.isChecked = false }
                }
            )
        )
        appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val appsScroll = ScrollView(this).apply {
            background = rounded(R.color.surface, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(280)
            ).apply { setMargins(0, dp(10), 0, 0) }
            addView(appsBox, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        appsCard.addView(appsScroll)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderApps(s?.toString().orEmpty())
            }
        })
        root.addView(appsCard)

        val durationCard = card(getString(R.string.duration_card))
        durationInput = EditText(this).apply {
            hint = getString(R.string.minutes_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("30")
            setTextColor(color(R.color.text))
            background = rounded(R.color.surface_2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        durationCard.addView(
            durationInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        durationCard.addView(
            withTopMargin(
                buttonRow(
                    actionButton(getString(R.string.duration_15), R.color.surface_2) { durationInput.setText("15") },
                    actionButton(getString(R.string.duration_30), R.color.surface_2) { durationInput.setText("30") },
                    actionButton(getString(R.string.duration_60), R.color.surface_2) { durationInput.setText("60") },
                    actionButton(getString(R.string.duration_120), R.color.surface_2) { durationInput.setText("120") }
                ),
                dp(8)
            )
        )
        root.addView(durationCard)

        val scheduleCard = card(getString(R.string.schedule_card))
        scheduleText = body(getString(R.string.schedule_off))
        scheduleCard.addView(scheduleText)
        scheduleCard.addView(
            withTopMargin(
                buttonRow(
                    actionButton(getString(R.string.start_time), R.color.surface_2) { pickScheduleTime(true) },
                    actionButton(getString(R.string.end_time), R.color.surface_2) { pickScheduleTime(false) }
                ),
                dp(8)
            )
        )
        scheduleCard.addView(
            withTopMargin(
                buttonRow(
                    actionButton(getString(R.string.activate_schedule), R.color.primary) { activateSchedule() },
                    actionButton(getString(R.string.cancel_schedule), R.color.surface_2) { clearSchedule() }
                ),
                dp(6)
            )
        )
        root.addView(scheduleCard)

        val cleanCard = card(getString(R.string.cleanup_card))
        usageText = body("—")
        cleanCard.addView(usageText)
        val usageButton = actionButton(getString(R.string.usage_stats_button), R.color.surface_2) {
            showUsageInfo()
        }
        usageButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, 0) }
        cleanCard.addView(usageButton)
        val cleanButton = actionButton(getString(R.string.cleanup_button), R.color.primary) {
            showCleanupDialog()
        }
        cleanButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, 0) }
        cleanCard.addView(cleanButton)
        root.addView(cleanCard)

        val deviceCard = card(getString(R.string.device_card))
        deviceText = body("—")
        deviceCard.addView(deviceText)
        root.addView(deviceCard)

        val statsCard = card(getString(R.string.focus_stats_card))
        statsText = body("—")
        statsCard.addView(statsText)
        root.addView(statsCard)

        root.addView(
            body(getString(R.string.version_line), R.color.muted, 12f)
                .apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) }
        )

        return ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg))
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun updateStatus() {
        val active = session.isActive()

        timerText.text = if (active) formatTime(session.remainingMillis()) else getString(R.string.no_active_session)
        progressBar.progress = if (active) session.progressPercent() else 0

        statusText.text = getString(R.string.owner_line, session.ownerName) + "\n" +
            getString(R.string.blocked_count_line, session.blockedPackages().size)

        permissionText.text = permissionSummary()

        val (start, end) = session.schedule()
        scheduleText.text = if (start >= 0 && end > start) {
            getString(R.string.schedule_range, formatMinute(start), formatMinute(end))
        } else {
            getString(R.string.schedule_off)
        }

        strictCheck.isChecked = session.strictMode
        usageText.text = usageSummary()
        deviceText.text = deviceSummary()
        statsText.text = focusSummary()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        allApps = pm.getInstalledPackages(0)
            .asSequence()
            .mapNotNull { info ->
                val ai = info.applicationInfo ?: return@mapNotNull null
                if (!ai.enabled) return@mapNotNull null
                val systemApp = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
                if (systemApp && info.packageName != packageName) return@mapNotNull null
                try {
                    AppItem(ai.loadLabel(pm).toString(), info.packageName, ai.loadIcon(pm))
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    private fun renderApps(query: String) {
        appsBox.removeAllViews()
        appChecks.clear()
        val q = query.trim().lowercase(Locale.getDefault())
        val selected = session.blockedPackages()

        for (item in allApps) {
            if (q.isNotEmpty() && !item.label.lowercase(Locale.getDefault()).contains(q)) continue

            val check = CheckBox(this).apply {
                text = item.label
                isChecked = selected.contains(item.packageName)
                tag = item.packageName
                textSize = 14f
                setTextColor(color(R.color.text))
                buttonTintList = ColorStateList.valueOf(color(R.color.primary))
            }
            val icon = ImageView(this).apply {
                setImageDrawable(item.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(R.color.surface_2, 10)
                addView(icon)
                addView(check, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            appChecks[item.packageName] = check
            appsBox.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun selectedPackages(): Set<String> =
        appChecks.filterValues { it.isChecked }.keys.toSet()

    private fun startFocusSession() {
        if (!isAccessibilityEnabled()) {
            toast(getString(R.string.enable_accessibility_first))
            return
        }
        val minutes = durationInput.text.toString().trim().toIntOrNull() ?: 30
        val result = session.start(minutes, selectedPackages())
        val message = when (result) {
            SessionManager.StartResult.Success -> {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, TimerService::class.java)
                )
                getString(R.string.session_started, minutes)
            }
            SessionManager.StartResult.AlreadyRunning -> getString(R.string.already_running)
            SessionManager.StartResult.InvalidDuration -> getString(R.string.invalid_duration)
            SessionManager.StartResult.NoAppsSelected -> getString(R.string.no_apps_selected)
        }
        toast(message)
        updateStatus()
    }

    private fun stopSession() {
        session.stop()
        updateStatus()
        toast(getString(R.string.session_stopped))
    }

    private fun requestAllPermissions() {
        if (!isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (!hasUsageStatsAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !isIgnoringBatteryOptimization()
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        toast(getString(R.string.permissions_hint))
    }

    private fun permissionSummary(): String {
        val accessibility = if (isAccessibilityEnabled()) R.string.perm_on else R.string.perm_off
        val notifications = if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) R.string.perm_on else R.string.perm_off
        val battery = if (isIgnoringBatteryOptimization()) R.string.perm_on else R.string.perm_off
        val usage = if (hasUsageStatsAccess()) R.string.perm_on else R.string.perm_off
        return getString(
            R.string.permission_summary,
            getString(accessibility),
            getString(usage),
            getString(notifications),
            getString(battery)
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        } catch (_: Settings.SettingNotFoundException) {
            0
        }
        if (enabled == 0) return false
        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "$packageName/${AppBlockingAccessibilityService::class.java.name}"
        return services.split(':').any { it.equals(target, ignoreCase = true) }
    }

    private fun isIgnoringBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun pickScheduleTime(isStart: Boolean) {
        val now = Calendar.getInstance()
        val (start, end) = session.schedule()
        val initialHour = if (isStart && start >= 0) start / 60 else now.get(Calendar.HOUR_OF_DAY)
        val initialMinute = if (isStart && start >= 0) start % 60 else now.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val minutesOfDay = hour * 60 + minute
                val (oldStart, oldEnd) = session.schedule()
                val newStart = if (isStart) minutesOfDay else if (oldStart >= 0) oldStart else 0
                val newEnd = if (isStart) {
                    if (oldEnd > oldStart) oldEnd else (newStart + 60) % (24 * 60)
                } else {
                    minutesOfDay
                }
                session.setSchedule(newStart, newEnd)
                scheduleDaily(newStart)
                updateStatus()
                toast(getString(R.string.schedule_saved))
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    private fun activateSchedule() {
        val (start, end) = session.schedule()
        if (start < 0 || end <= start) {
            toast(getString(R.string.schedule_pick_times))
            return
        }
        if (!isAccessibilityEnabled()) {
            toast(getString(R.string.enable_blocking_first))
            return
        }
        scheduleDaily(start)
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (current in start until end) {
            val result = session.start(end - current, selectedPackages())
            if (result is SessionManager.StartResult.Success) {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, TimerService::class.java)
                )
                toast(getString(R.string.schedule_applied))
            } else {
                toast(getString(R.string.schedule_cannot_start, result.toString()))
            }
        } else {
            toast(getString(R.string.schedule_enabled))
        }
        updateStatus()
    }

    private fun clearSchedule() {
        session.setSchedule(-1, -1)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            200,
            Intent(this, ScheduleReceiver::class.java).setAction("com.assem.copyapp.START_SCHEDULE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        updateStatus()
        toast(getString(R.string.schedule_cancelled))
    }

    private fun scheduleDaily(startMin: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMin / 60)
            set(Calendar.MINUTE, startMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            200,
            Intent(this, ScheduleReceiver::class.java).setAction("com.assem.copyapp.START_SCHEDULE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun hasUsageStatsAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun usageSummary(): String {
        if (!hasUsageStatsAccess()) return getString(R.string.usage_not_enabled)
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            from,
            System.currentTimeMillis()
        ).orEmpty()
        val totals = stats
            .groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.sumOf { it.totalTimeInForeground } }
            .filter { (pkg, ms) -> ms > 0 && !session.isEssential(pkg) }
            .sortedByDescending { it.second }
        if (totals.isEmpty()) return getString(R.string.no_usage_data)
        val total = totals.sumOf { it.second }
        val top = totals.take(4).joinToString("\n") {
            "• ${labelFor(it.first)} — ${formatDuration(it.second)}"
        }
        return getString(R.string.screen_time_today, formatDuration(total)) + "\n$top"
    }

    private fun showUsageInfo() {
        if (!hasUsageStatsAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            toast(getString(R.string.usage_enable_hint))
        } else {
            usageText.text = usageSummary()
            toast(getString(R.string.usage_refreshed))
        }
    }

    private fun showCleanupDialog() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val processes = activityManager.runningAppProcesses.orEmpty()
            .filter {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
            }
            .map { it.processName }
            .filter { name ->
                name != packageName &&
                    !session.isEssential(name) &&
                    !name.startsWith("com.google.android.gms") &&
                    !name.startsWith("android.")
            }
            .distinct()
            .take(10)
        if (processes.isEmpty()) {
            toast(getString(R.string.cleanup_empty))
            return
        }
        val labels = processes.map { labelFor(it) }.toTypedArray()
        val checked = BooleanArray(processes.size) { true }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cleanup_title))
            .setMessage(getString(R.string.cleanup_message))
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton(getString(R.string.cleanup_action)) { _, _ ->
                var count = 0
                for (index in processes.indices) {
                    if (checked[index]) {
                        activityManager.killBackgroundProcesses(processes[index])
                        count++
                    }
                }
                toast(getString(R.string.cleanup_done, count))
                updateStatus()
            }
            .setNegativeButton(getString(R.string.cleanup_cancel), null)
            .show()
    }

    private fun deviceSummary(): String {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingText = getString(if (plugged > 0) R.string.battery_charging else R.string.battery_discharging)
        val batteryText = if (percent >= 0) "$percent%" else getString(R.string.battery_unknown)
        val storage = StatFs(Environment.getDataDirectory().path)
        val freeGb = storage.availableBytes / (1024f * 1024f * 1024f)
        val totalGb = storage.totalBytes / (1024f * 1024f * 1024f)
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val availableMb = memory.availMem / (1024f * 1024f)
        return getString(R.string.battery_line, batteryText, chargingText) + "\n" +
            getString(
                R.string.storage_line,
                String.format(Locale.getDefault(), "%.1f", freeGb),
                String.format(Locale.getDefault(), "%.1f", totalGb)
            ) + "\n" +
            getString(R.string.memory_line, availableMb.toInt())
    }

    private fun focusSummary(): String {
        val history = session.history()
        val lines = history.take(3).joinToString("\n") {
            getString(R.string.history_line, it.first, it.second, it.third)
        }
        return getString(R.string.sessions_today, session.sessionsToday()) + "\n" +
            getString(R.string.focus_minutes_today, session.focusMinutesToday()) + "\n" +
            if (lines.isEmpty()) getString(R.string.no_history)
            else getString(R.string.last_sessions) + "\n$lines"
    }

    private fun labelFor(packageName: String): String =
        allApps.firstOrNull { it.packageName == packageName }?.label ?: try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }

    private fun formatDuration(millis: Long): String {
        val minutes = millis / 60_000L
        return if (minutes >= 60) "${minutes / 60}س ${minutes % 60}د" else "$minutes د"
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

    private fun formatMinute(minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

    private fun color(res: Int): Int = ContextCompat.getColor(this, res)

    private fun rounded(colorRes: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color(colorRes))
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun body(text: String, colorRes: Int = R.color.muted, size: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color(colorRes))
        }

    private fun card(title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.surface, 18)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color(R.color.text))
                    setPadding(0, 0, 0, dp(12))
                }
            )
        }

    private fun actionButton(text: String, colorRes: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(color(R.color.text))
            background = rounded(colorRes, 12)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(3), 0, dp(3), 0) }
            setOnClickListener { onClick() }
        }

    private fun buttonRow(vararg buttons: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            buttons.forEach {
                addView(
                    it,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(dp(3), 0, dp(3), 0) }
                )
            }
        }

    private fun <T : android.view.View> withTopMargin(view: T, top: Int): T = view.apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, top, 0, 0) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
