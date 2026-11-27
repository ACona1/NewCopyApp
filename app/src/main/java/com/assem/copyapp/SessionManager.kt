package com.assem.copyapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val sp = Prefs.get(appContext)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val ownerName: String
        get() = appContext.getString(R.string.owner_name)

    val strictMode: Boolean
        get() = sp.getBoolean(Prefs.KEY_STRICT_MODE, false)

    fun setStrictMode(enabled: Boolean) {
        sp.edit().putBoolean(Prefs.KEY_STRICT_MODE, enabled).apply()
    }

    fun blockedPackages(): Set<String> =
        sp.getStringSet(Prefs.KEY_BLOCKED, emptySet()) ?: emptySet()

    fun setBlockedPackages(packages: Set<String>) {
        sp.edit().putStringSet(Prefs.KEY_BLOCKED, packages).apply()
    }

    fun sessionStart(): Long = sp.getLong(Prefs.KEY_SESSION_START, 0L)

    fun sessionEnd(): Long = sp.getLong(Prefs.KEY_SESSION_END, 0L)

    fun isActive(): Boolean = sessionEnd() > System.currentTimeMillis()

    fun finalizeExpired(): Boolean {
        val start = sessionStart()
        val end = sessionEnd()
        if (end != 0L && end <= System.currentTimeMillis()) {
            if (end > start) recordCompletion(((end - start) / 60_000L).toInt().coerceAtLeast(1))
            clearSessionOnly()
            return true
        }
        return false
    }

    fun remainingMillis(): Long {
        val end = sessionEnd()
        return if (end > System.currentTimeMillis()) end - System.currentTimeMillis() else 0L
    }

    fun progressPercent(): Int {
        val start = sessionStart()
        val end = sessionEnd()
        if (end <= start) return 0
        val now = System.currentTimeMillis().coerceIn(start, end)
        return ((now - start) * 100 / (end - start)).toInt().coerceIn(0, 100)
    }

    fun isEssential(packageName: String?): Boolean =
        !packageName.isNullOrBlank() && essentialPackages().contains(packageName)

    fun isBlocked(packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || !isActive()) return false
        if (isEssential(packageName)) return false
        return if (strictMode) true else blockedPackages().contains(packageName)
    }

    fun start(durationMinutes: Int, packages: Set<String>): StartResult {
        if (isActive()) return StartResult.AlreadyRunning
        if (durationMinutes <= 0) return StartResult.InvalidDuration
        if (!strictMode && packages.isEmpty()) return StartResult.NoAppsSelected

        val now = System.currentTimeMillis()
        val end = now + durationMinutes * 60_000L
        sp.edit()
            .putLong(Prefs.KEY_SESSION_START, now)
            .putLong(Prefs.KEY_SESSION_END, end)
            .putStringSet(Prefs.KEY_BLOCKED, packages)
            .putInt(Prefs.KEY_DAILY_COUNT, todayCount() + 1)
            .putString(Prefs.KEY_DAILY_DATE, today())
            .apply()
        return StartResult.Success
    }

    fun stop() {
        val start = sessionStart()
        val end = sessionEnd()
        if (start > 0 && end > start) {
            val elapsed = ((minOf(System.currentTimeMillis(), end) - start) / 60_000L)
                .toInt().coerceAtLeast(1)
            recordCompletion(elapsed)
        }
        clearSessionOnly()
        appContext.sendBroadcast(Intent(BlockingActivity.ACTION_CLOSE))
    }

    fun setSchedule(startMin: Int, endMin: Int) {
        sp.edit()
            .putInt(Prefs.KEY_SCHEDULE_START, startMin)
            .putInt(Prefs.KEY_SCHEDULE_END, endMin)
            .apply()
    }

    fun schedule(): Pair<Int, Int> =
        sp.getInt(Prefs.KEY_SCHEDULE_START, -1) to sp.getInt(Prefs.KEY_SCHEDULE_END, -1)

    fun sessionsToday(): Int = todayCount()

    fun focusMinutesToday(): Int =
        if (sp.getString(Prefs.KEY_FOCUS_DATE, "") == today()) {
            sp.getInt(Prefs.KEY_FOCUS_MINUTES, 0)
        } else {
            0
        }

    fun history(): List<Triple<String, Int, Int>> =
        (sp.getString(Prefs.KEY_HISTORY, "") ?: "")
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size == 3) {
                    Triple(
                        parts[0],
                        parts[1].toIntOrNull() ?: 0,
                        parts[2].toIntOrNull() ?: 0
                    )
                } else {
                    null
                }
            }

    private fun recordCompletion(minutes: Int) {
        if (sp.getString(Prefs.KEY_FOCUS_DATE, "") != today()) {
            sp.edit()
                .putString(Prefs.KEY_FOCUS_DATE, today())
                .putInt(Prefs.KEY_FOCUS_MINUTES, 0)
                .apply()
        }
        sp.edit()
            .putInt(Prefs.KEY_FOCUS_MINUTES, sp.getInt(Prefs.KEY_FOCUS_MINUTES, 0) + minutes)
            .apply()
        val entry = Triple(today(), minutes, blockedPackages().size)
        val items = history().toMutableList()
        items.add(0, entry)
        sp.edit().putString(
            Prefs.KEY_HISTORY,
            items.take(5).joinToString(";") { "${it.first}|${it.second}|${it.third}" }
        ).apply()
    }

    private fun essentialPackages(): Set<String> {
        val result = mutableSetOf(appContext.packageName, "com.android.systemui")
        val pm = appContext.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:") }
        )
        for (intent in intents) {
            try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.let { result.add(it) }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun today(): String = dateFormat.format(Date())

    private fun todayCount(): Int {
        val savedDate = sp.getString(Prefs.KEY_DAILY_DATE, "")
        return if (savedDate == today()) sp.getInt(Prefs.KEY_DAILY_COUNT, 0) else 0
    }

    private fun clearSessionOnly() {
        sp.edit()
            .putLong(Prefs.KEY_SESSION_START, 0L)
            .putLong(Prefs.KEY_SESSION_END, 0L)
            .apply()
    }

    sealed class StartResult {
        object Success : StartResult()
        object AlreadyRunning : StartResult()
        object InvalidDuration : StartResult()
        object NoAppsSelected : StartResult()
    }
}
