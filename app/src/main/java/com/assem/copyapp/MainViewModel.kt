package com.assem.copyapp

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assem.copyapp.data.AppRepository
import com.assem.copyapp.data.BreakEntity
import com.assem.copyapp.data.DailyLimitEntity
import com.assem.copyapp.data.ProfileEntity
import com.assem.copyapp.data.RuleEntity
import com.assem.copyapp.data.UsageEventEntity
import com.assem.copyapp.domain.DetectorConfig
import com.assem.copyapp.domain.DetectorType
import com.assem.copyapp.domain.RuleAction
import com.assem.copyapp.domain.ScoreCalculator
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val context: Context,
    private val repository: AppRepository,
    private val controller: SessionController
) : ViewModel() {

    data class AppInfo(val label: String, val packageName: String)

    val settings = repository.settingsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.assem.copyapp.data.AppSettings()
    )
    val session = repository.sessionFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.assem.copyapp.data.SessionState()
    )
    val profiles = repository.profiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules = repository.rules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val detectors = repository.detectors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val limits = repository.dailyLimits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions = repository.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events = repository.usageEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val breaks = repository.breaks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _search = MutableStateFlow("")
    val search = _search.asStateFlow()

    private val _duration = MutableStateFlow(30)
    val duration = _duration.asStateFlow()

    private val _mode = MutableStateFlow(SessionMode.SOFT)
    val mode = _mode.asStateFlow()

    private val _strict = MutableStateFlow(false)
    val strict = _strict.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()

    private val _scheduleStart = MutableStateFlow(-1)
    val scheduleStart = _scheduleStart.asStateFlow()

    private val _scheduleEnd = MutableStateFlow(-1)
    val scheduleEnd = _scheduleEnd.asStateFlow()

    private val _usageSummary = MutableStateFlow("")
    val usageSummary = _usageSummary.asStateFlow()

    private val _deviceSummary = MutableStateFlow("")
    val deviceSummary = _deviceSummary.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = settings.first()
            _duration.value = saved.defaultDurationMinutes
            _mode.value = saved.defaultMode
            _strict.value = saved.strictByDefault
            profiles.first().firstOrNull()?.let {
                _scheduleStart.value = it.scheduleStart
                _scheduleEnd.value = it.scheduleEnd
            }
            loadApps()
            refreshInsights()
            controller.restoreAfterBoot()
        }
        viewModelScope.launch {
            session.collect { _selected.value = it.blocked.toSet() }
        }
    }

    private suspend fun loadApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        _apps.value = pm.getInstalledPackages(0)
            .asSequence()
            .mapNotNull { info ->
                val ai = info.applicationInfo ?: return@mapNotNull null
                if (!ai.enabled) return@mapNotNull null
                val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
                if (system && info.packageName != context.packageName) return@mapNotNull null
                val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(info.packageName)
                AppInfo(label, info.packageName)
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    fun filteredApps(query: String): List<AppInfo> {
        val q = query.trim().lowercase(Locale.getDefault())
        return if (q.isEmpty()) apps.value else apps.value.filter {
            it.label.lowercase(Locale.getDefault()).contains(q) || it.packageName.contains(q)
        }
    }

    fun setSearch(value: String) { _search.value = value }
    fun setDuration(value: Int) { _duration.value = value.coerceIn(1, 600) }
    fun setMode(value: SessionMode) { _mode.value = value }
    fun setStrict(value: Boolean) { _strict.value = value }

    fun togglePackage(pkg: String) {
        if (session.value.active) return
        _selected.value = if (pkg in _selected.value) _selected.value - pkg else _selected.value + pkg
    }

    fun selectAll() {
        if (session.value.active) return
        _selected.value = apps.value.map { it.packageName }.toSet()
    }

    fun clearSelection() {
        if (session.value.active) return
        _selected.value = emptySet()
    }

    fun startFocus() = viewModelScope.launch(Dispatchers.IO) {
        if (!isAccessibilityEnabled()) {
            _message.emit(context.getString(R.string.enable_accessibility_first))
            return@launch
        }
        val result = controller.start(
            _duration.value,
            _selected.value,
            _mode.value,
            _strict.value
        )
        val text = when (result) {
            is SessionController.StartResult.Success ->
                context.getString(R.string.session_started, _duration.value)
            is SessionController.StartResult.AlreadyRunning -> context.getString(R.string.already_running)
            is SessionController.StartResult.InvalidDuration -> context.getString(R.string.invalid_duration)
            is SessionController.StartResult.NoAppsSelected -> context.getString(R.string.no_apps_selected)
            is SessionController.StartResult.Locked -> context.getString(R.string.hard_mode_locked)
        }
        _message.emit(text)
        if (result is SessionController.StartResult.Success) {
            FocusWidgetProvider.refresh(context)
        }
    }

    fun endFocus(reason: String = "SOFT_FRICTION") = viewModelScope.launch(Dispatchers.IO) {
        val ok = controller.end(reason, fromUser = reason != "TIME")
        _message.emit(context.getString(if (ok) R.string.session_stopped else R.string.hard_mode_locked))
    }

    fun saveSchedule(start: Int, end: Int) = viewModelScope.launch(Dispatchers.IO) {
        val existing = profiles.first().firstOrNull()
        val profile = ProfileEntity(
            id = existing?.id ?: 0L,
            name = existing?.name ?: context.getString(R.string.default_profile),
            mode = _mode.value.name,
            durationMinutes = _duration.value,
            blockedCsv = _selected.value.joinToString(","),
            strict = _strict.value,
            scheduleStart = start,
            scheduleEnd = end
        )
        repository.saveProfile(profile)
        _scheduleStart.value = start
        _scheduleEnd.value = end
        scheduleAlarm(start)
        _message.emit(context.getString(R.string.schedule_saved))
    }

    fun clearSchedule() = viewModelScope.launch(Dispatchers.IO) {
        val existing = profiles.first().firstOrNull() ?: return@launch
        repository.saveProfile(existing.copy(scheduleStart = -1, scheduleEnd = -1))
        _scheduleStart.value = -1
        _scheduleEnd.value = -1
        cancelAlarm()
        _message.emit(context.getString(R.string.schedule_cancelled))
    }

    private fun scheduleAlarm(startMin: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMin / 60)
            set(Calendar.MINUTE, startMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
        val pending = PendingIntent.getBroadcast(
            context,
            200,
            Intent(context, ScheduleReceiver::class.java).setAction("com.assem.copyapp.START_SCHEDULE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
    }

    private fun cancelAlarm() {
        val pending = PendingIntent.getBroadcast(
            context,
            200,
            Intent(context, ScheduleReceiver::class.java).setAction("com.assem.copyapp.START_SCHEDULE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    fun saveRule(pkg: String, section: String, action: RuleAction, start: Int, end: Int, days: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRule(
                RuleEntity(
                    packageName = pkg.trim(),
                    sectionId = section.trim(),
                    action = action.name,
                    startMin = start,
                    endMin = end,
                    daysMask = days.coerceIn(1, 0x7F)
                )
            )
            _message.emit(context.getString(R.string.rule_saved))
        }

    fun deleteRule(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteRule(id)
        _message.emit(context.getString(R.string.rule_deleted))
    }

    fun saveDetector(pkg: String, section: String, type: DetectorType, markers: String) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveDetector(
                DetectorConfig(
                    id = 0L,
                    packageName = pkg.trim(),
                    sectionId = section.trim(),
                    type = type,
                    markers = markers.split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toSet()
                )
            )
            repository.updateSettings { copy(lastDetectorPackage = pkg.trim()) }
            _message.emit(context.getString(R.string.detector_saved))
        }

    fun deleteDetector(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDetector(id)
        _message.emit(context.getString(R.string.detector_deleted))
    }

    fun setDailyLimit(pkg: String, limitMinutes: Int) = viewModelScope.launch(Dispatchers.IO) {
        repository.setDailyLimit(today(), pkg.trim(), limitMinutes.coerceAtLeast(0))
        _message.emit(context.getString(R.string.limit_saved))
    }

    fun addException(pkg: String) = viewModelScope.launch(Dispatchers.IO) {
        if (pkg.isBlank()) return@launch
        repository.updateSettings { copy(userExceptions = userExceptions + pkg.trim()) }
        _message.emit(context.getString(R.string.exception_saved))
    }

    fun removeException(pkg: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateSettings { copy(userExceptions = userExceptions - pkg) }
    }

    fun finishOnboarding() = viewModelScope.launch(Dispatchers.IO) {
        repository.updateSettings { copy(onboardingDone = true) }
    }

    fun updateSettings(transform: (com.assem.copyapp.data.AppSettings) -> com.assem.copyapp.data.AppSettings) =
        viewModelScope.launch(Dispatchers.IO) { repository.updateSettings(transform) }

    fun setFriendKey(code: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.settings.setFriendKey(code)
        _message.emit(context.getString(R.string.friend_key_saved))
    }

    fun addCount(section: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.addUsage(
            UsageEventEntity(
                packageName = "manual",
                sectionId = section,
                kind = section.uppercase(Locale.getDefault()),
                amount = 1,
                atWall = System.currentTimeMillis(),
                scoreDelta = 1
            )
        )
        _message.emit(context.getString(R.string.count_saved))
    }

    fun exportJson(onResult: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        onResult(repository.exportJson())
    }

    fun importJson(json: String) = viewModelScope.launch(Dispatchers.IO) {
        val result = repository.importJson(json)
        _message.emit(
            context.getString(
                if (result.isSuccess) R.string.import_ok else R.string.import_failed
            )
        )
    }

    fun refreshInsights() = viewModelScope.launch(Dispatchers.IO) {
        _usageSummary.value = buildUsageSummary()
        _deviceSummary.value = buildDeviceSummary()
    }

    private fun buildUsageSummary(): String {
        if (!hasUsageStatsAccess()) return context.getString(R.string.usage_not_enabled)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, System.currentTimeMillis()).orEmpty()
        }.getOrDefault(emptyList())
        val totals = stats
            .groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.sumOf { it.totalTimeInForeground } }
            .filter { (_, ms) -> ms > 0 }
            .sortedByDescending { it.second }
        if (totals.isEmpty()) return context.getString(R.string.no_usage_data)
        val top = totals.take(5).joinToString("\n") { "• ${labelFor(it.first)} — ${formatMinutes(it.second)}" }
        return context.getString(R.string.screen_time_today, formatMinutes(totals.sumOf { it.second })) + "\n" + top
    }

    private fun buildDeviceSummary(): String {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = context.getString(if (plugged > 0) R.string.battery_charging else R.string.battery_discharging)
        val batteryText = if (percent >= 0) "$percent%" else context.getString(R.string.battery_unknown)
        val storage = StatFs(Environment.getDataDirectory().path)
        val freeGb = storage.availableBytes / (1024f * 1024f * 1024f)
        val totalGb = storage.totalBytes / (1024f * 1024f * 1024f)
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val availableMb = memory.availMem / (1024f * 1024f)
        return context.getString(R.string.battery_line, batteryText, charging) + "\n" +
            context.getString(
                R.string.storage_line,
                String.format(Locale.getDefault(), "%.1f", freeGb),
                String.format(Locale.getDefault(), "%.1f", totalGb)
            ) + "\n" +
            context.getString(R.string.memory_line, availableMb.toInt())
    }

    fun cleanupBackground() = viewModelScope.launch(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val processes = am.runningAppProcesses.orEmpty()
            .filter { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND }
            .map { it.processName }
            .filter { it != context.packageName && !it.startsWith("android.") && !it.startsWith("com.google.android.gms") }
            .distinct()
            .take(10)
        if (processes.isEmpty()) {
            _message.emit(context.getString(R.string.cleanup_empty))
            return@launch
        }
        processes.forEach { am.killBackgroundProcesses(it) }
        _message.emit(context.getString(R.string.cleanup_done, processes.size))
    }

    fun brainRotScore(): Int {
        val list = events.value
        val input = ScoreCalculator.ScoreInput(
            reels = list.count { it.sectionId.equals("reels", true) },
            shorts = list.count { it.sectionId.equals("shorts", true) },
            feedScrolls = list.count { it.kind.equals("FEED_SCROLL", true) },
            breaks = breaks.value.size,
            nightMinutes = sessions.value.sumOf { it.actualMinutes } / 10,
            budgetBreaches = list.count { it.kind.equals("BUDGET_BREACH", true) },
            focusMinutes = sessions.value.sumOf { it.actualMinutes }
        )
        return ScoreCalculator.score(input)
    }

    fun isAccessibilityEnabled(): Boolean {
        val enabled = runCatching {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        }.getOrDefault(0)
        if (enabled == 0) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${context.packageName}/${AppBlockingAccessibilityService::class.java.name}"
        return services.split(':').any { it.equals(target, ignoreCase = true) }
    }

    fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }

    fun isIgnoringBattery(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun permissionIntents(): List<Intent> {
        val list = mutableListOf<Intent>()
        if (!isAccessibilityEnabled()) list.add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        if (!hasUsageStatsAccess()) list.add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBattery()) {
            list.add(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        return list
    }

    fun labelFor(pkg: String): String =
        apps.value.firstOrNull { it.packageName == pkg }?.label ?: runCatching {
            context.packageManager.getApplicationInfo(pkg, 0)
                .loadLabel(context.packageManager).toString()
        }.getOrDefault(pkg)

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun formatMinutes(millis: Long): String {
        val minutes = millis / 60_000L
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    fun formatClock(minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

    class Factory(
        private val context: Context,
        private val repository: AppRepository,
        private val controller: SessionController
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context.applicationContext, repository, controller) as T
        }
    }
}
