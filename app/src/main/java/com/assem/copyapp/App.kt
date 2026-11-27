package com.assem.copyapp

import android.app.Application
import android.content.Context
import com.assem.copyapp.data.AppDatabase
import com.assem.copyapp.data.AppRepository
import com.assem.copyapp.data.SessionStore
import com.assem.copyapp.data.SettingsStore
import com.assem.copyapp.domain.AllowlistProvider
import com.assem.copyapp.domain.ClockGuard
import com.assem.copyapp.domain.DetectorRegistry
import com.assem.copyapp.domain.EnforcementEngine
import com.assem.copyapp.domain.NodeInfoSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startEngineSync()
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)
    val settingsStore = SettingsStore(appContext)
    val sessionStore = SessionStore(appContext)
    val repository = AppRepository(database, settingsStore, sessionStore)

    val clockGuard = ClockGuard()
    val allowlistProvider = AllowlistProvider(appContext)
    val detectorRegistry = DetectorRegistry()
    val enforcementEngine = EnforcementEngine(clockGuard, allowlistProvider, detectorRegistry)

    val lastNode = MutableStateFlow<NodeInfoSnapshot?>(null)

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sessionController = SessionController(appContext, repository, clockGuard, enforcementEngine)

    fun startEngineSync() {
        syncScope.launch {
            combine(
                repository.sessionFlow(),
                repository.settingsFlow(),
                repository.rules(),
                repository.detectors(),
                repository.dailyLimits()
            ) { session, settings, rules, detectors, limits ->
                val now = clockGuard.nowWall()
                val minOfDay = clockGuard.minutesOfDay(now)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))
                val todayLimits = limits.filter { it.date == today }
                detectorRegistry.update(detectors)

                val activeRules = com.assem.copyapp.domain.RuleEvaluator.activeRules(
                    clockGuard,
                    rules.map {
                        com.assem.copyapp.domain.RuleSnapshot(
                            packageName = it.packageName,
                            sectionId = it.sectionId,
                            action = runCatching {
                                com.assem.copyapp.domain.RuleAction.valueOf(it.action)
                            }.getOrDefault(com.assem.copyapp.domain.RuleAction.BLOCK_APP),
                            startMin = it.startMin,
                            endMin = it.endMin,
                            daysMask = it.daysMask,
                            enabled = it.enabled
                        )
                    },
                    now
                ) + com.assem.copyapp.domain.RuleEvaluator.deriveNightRules(
                    settings.windDownEnabled,
                    settings.windDownStartMin,
                    settings.bedtimeMin,
                    minOfDay
                )

                com.assem.copyapp.domain.EnforcerSnapshot(
                    session = com.assem.copyapp.domain.SessionSnapshot(
                        active = session.active,
                        startWall = session.startWall,
                        endWall = session.endWall,
                        startMono = session.startMono,
                        mode = session.mode,
                        blocked = session.blocked,
                        strict = session.strict,
                        breaks = session.breaks
                    ),
                    allowlist = allowlistProvider.systemAllowlist(),
                    userExceptions = settings.userExceptions,
                    rules = activeRules,
                    budgets = todayLimits.associate { it.packageName to it.limitMinutes },
                    usedMinutes = todayLimits.associate { it.packageName to it.usedMinutes },
                    pauseUntilMono = session.pauseUntilMono,
                    elapsedRealtime = clockGuard.nowMono(),
                    nowWall = now,
                    windDownEnabled = settings.windDownEnabled,
                    windDownStartMin = settings.windDownStartMin,
                    bedtimeMin = settings.bedtimeMin
                )
            }.collect { snapshot -> enforcementEngine.update(snapshot) }
        }
    }
}
