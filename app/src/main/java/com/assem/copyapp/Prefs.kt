package com.assem.copyapp

import android.content.Context

object Prefs {
    private const val NAME = "new_copy_app_prefs"

    const val KEY_PREMIUM = "is_premium"
    const val KEY_OWNER = "owner_name"
    const val KEY_BLOCKED = "blocked_packages"
    const val KEY_SESSION_START = "session_start"
    const val KEY_SESSION_END = "session_end"
    const val KEY_SCHEDULE_START = "schedule_start_min"
    const val KEY_SCHEDULE_END = "schedule_end_min"
    const val KEY_DAILY_COUNT = "daily_count"
    const val KEY_DAILY_DATE = "daily_date"
    const val KEY_STRICT_MODE = "strict_mode"
    const val KEY_HISTORY = "session_history"
    const val KEY_FOCUS_MINUTES = "focus_minutes_today"
    const val KEY_FOCUS_DATE = "focus_date"

    fun get(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
