package com.assem.copyapp

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager(context: Context) {
    private val sp = Prefs.get(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val ownerName: String
        get() = sp.getString(Prefs.KEY_OWNER, "عاصم") ?: "عاصم"

    val isPremium: Boolean
        get() = sp.getBoolean(Prefs.KEY_PREMIUM, false)

    val maxMinutes: Int
        get() = if (isPremium) 24 * 60 else 6 * 60

    fun blockedPackages(): Set<String> =
        sp.getStringSet(Prefs.KEY_BLOCKED, emptySet()) ?: emptySet()

    fun setBlockedPackages(packages: Set<String>) {
        sp.edit().putStringSet(Prefs.KEY_BLOCKED, packages).apply()
    }

    fun sessionEnd(): Long = sp.getLong(Prefs.KEY_SESSION_END, 0L)

    fun isActive(): Boolean {
        val end = sessionEnd()
        if (end <= System.currentTimeMillis()) {
            if (end != 0L) clearSessionOnly()
            return false
        }
        return true
    }

    fun remainingMillis(): Long {
        val end = sessionEnd()
        return if (end > System.currentTimeMillis()) end - System.currentTimeMillis() else 0L
    }

    fun isBlocked(packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || !isActive()) return false
        return blockedPackages().contains(packageName)
    }

    fun start(durationMinutes: Int, packages: Set<String>): StartResult {
        if (isActive()) return StartResult.AlreadyRunning
        if (durationMinutes <= 0) return StartResult.InvalidDuration
        if (durationMinutes > maxMinutes) return StartResult.LimitExceeded
        if (!isPremium && todayCount() >= FREE_DAILY_LIMIT) return StartResult.DailyLimitReached
        if (packages.isEmpty()) return StartResult.NoAppsSelected

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
        clearSessionOnly()
    }

    fun redeem(code: String): Boolean {
        if (code.trim() == PROMO_CODE) {
            sp.edit()
                .putBoolean(Prefs.KEY_PREMIUM, true)
                .putString(Prefs.KEY_OWNER, "عاصم")
                .apply()
            return true
        }
        return false
    }

    fun setSchedule(startMin: Int, endMin: Int) {
        sp.edit()
            .putInt(Prefs.KEY_SCHEDULE_START, startMin)
            .putInt(Prefs.KEY_SCHEDULE_END, endMin)
            .apply()
    }

    fun schedule(): Pair<Int, Int> =
        sp.getInt(Prefs.KEY_SCHEDULE_START, -1) to sp.getInt(Prefs.KEY_SCHEDULE_END, -1)

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
        object LimitExceeded : StartResult()
        object DailyLimitReached : StartResult()
        object NoAppsSelected : StartResult()
    }

    companion object {
        const val PROMO_CODE = "gdNeooYe8"
        const val FREE_DAILY_LIMIT = 4
    }
}
