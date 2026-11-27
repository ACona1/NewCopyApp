package com.assem.copyapp.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

private const val KEY_ONBOARDING_DONE = "onboardingDone"
private const val KEY_DEFAULT_MODE = "defaultMode"
private const val KEY_DEFAULT_DURATION_MINUTES = "defaultDurationMinutes"
private const val KEY_STRICT_BY_DEFAULT = "strictByDefault"
private const val KEY_USER_EXCEPTIONS = "userExceptions"
private const val KEY_FRIEND_KEY_HASH = "friendKeyHash"
private const val KEY_WIND_DOWN_ENABLED = "windDownEnabled"
private const val KEY_WIND_DOWN_START_MIN = "windDownStartMin"
private const val KEY_BEDTIME_MIN = "bedtimeMin"
private const val KEY_PAUSE_REQUIRED_SECONDS = "pauseRequiredSeconds"
private const val KEY_NOTIFICATIONS_ENABLED = "notificationsEnabled"
private const val KEY_DIGEST_ENABLED = "digestEnabled"
private const val KEY_LANGUAGE = "language"
private const val KEY_LAST_DETECTOR_PACKAGE = "lastDetectorPackage"

private val PREF_ONBOARDING_DONE = booleanPreferencesKey(KEY_ONBOARDING_DONE)
private val PREF_DEFAULT_MODE = stringPreferencesKey(KEY_DEFAULT_MODE)
private val PREF_DEFAULT_DURATION_MINUTES = intPreferencesKey(KEY_DEFAULT_DURATION_MINUTES)
private val PREF_STRICT_BY_DEFAULT = booleanPreferencesKey(KEY_STRICT_BY_DEFAULT)
private val PREF_USER_EXCEPTIONS = stringSetPreferencesKey(KEY_USER_EXCEPTIONS)
private val PREF_FRIEND_KEY_HASH = stringPreferencesKey(KEY_FRIEND_KEY_HASH)
private val PREF_WIND_DOWN_ENABLED = booleanPreferencesKey(KEY_WIND_DOWN_ENABLED)
private val PREF_WIND_DOWN_START_MIN = intPreferencesKey(KEY_WIND_DOWN_START_MIN)
private val PREF_BEDTIME_MIN = intPreferencesKey(KEY_BEDTIME_MIN)
private val PREF_PAUSE_REQUIRED_SECONDS = intPreferencesKey(KEY_PAUSE_REQUIRED_SECONDS)
private val PREF_NOTIFICATIONS_ENABLED = booleanPreferencesKey(KEY_NOTIFICATIONS_ENABLED)
private val PREF_DIGEST_ENABLED = booleanPreferencesKey(KEY_DIGEST_ENABLED)
private val PREF_LANGUAGE = stringPreferencesKey(KEY_LANGUAGE)
private val PREF_LAST_DETECTOR_PACKAGE = stringPreferencesKey(KEY_LAST_DETECTOR_PACKAGE)

data class AppSettings(
    val onboardingDone: Boolean = false,
    val defaultMode: SessionMode = SessionMode.SOFT,
    val defaultDurationMinutes: Int = 30,
    val strictByDefault: Boolean = false,
    val userExceptions: Set<String> = emptySet(),
    val friendKeyHash: String = "",
    val windDownEnabled: Boolean = false,
    val windDownStartMin: Int = 1380,
    val bedtimeMin: Int = 1410,
    val pauseRequiredSeconds: Int = 20,
    val notificationsEnabled: Boolean = true,
    val digestEnabled: Boolean = false,
    val language: String = "system",
    val lastDetectorPackage: String = ""
)

private fun Preferences.toAppSettings(): AppSettings {
    val storedMode = this[PREF_DEFAULT_MODE] ?: SessionMode.SOFT.name
    return AppSettings(
        onboardingDone = this[PREF_ONBOARDING_DONE] ?: false,
        defaultMode = runCatching { SessionMode.valueOf(storedMode) }.getOrDefault(SessionMode.SOFT),
        defaultDurationMinutes = this[PREF_DEFAULT_DURATION_MINUTES] ?: 30,
        strictByDefault = this[PREF_STRICT_BY_DEFAULT] ?: false,
        userExceptions = this[PREF_USER_EXCEPTIONS] ?: emptySet(),
        friendKeyHash = this[PREF_FRIEND_KEY_HASH] ?: "",
        windDownEnabled = this[PREF_WIND_DOWN_ENABLED] ?: false,
        windDownStartMin = this[PREF_WIND_DOWN_START_MIN] ?: 1380,
        bedtimeMin = this[PREF_BEDTIME_MIN] ?: 1410,
        pauseRequiredSeconds = this[PREF_PAUSE_REQUIRED_SECONDS] ?: 20,
        notificationsEnabled = this[PREF_NOTIFICATIONS_ENABLED] ?: true,
        digestEnabled = this[PREF_DIGEST_ENABLED] ?: false,
        language = this[PREF_LANGUAGE] ?: "system",
        lastDetectorPackage = this[PREF_LAST_DETECTOR_PACKAGE] ?: ""
    )
}

private fun AppSettings.writeTo(prefs: MutablePreferences) {
    prefs[PREF_ONBOARDING_DONE] = onboardingDone
    prefs[PREF_DEFAULT_MODE] = defaultMode.name
    prefs[PREF_DEFAULT_DURATION_MINUTES] = defaultDurationMinutes
    prefs[PREF_STRICT_BY_DEFAULT] = strictByDefault
    prefs[PREF_USER_EXCEPTIONS] = userExceptions
    prefs[PREF_FRIEND_KEY_HASH] = friendKeyHash
    prefs[PREF_WIND_DOWN_ENABLED] = windDownEnabled
    prefs[PREF_WIND_DOWN_START_MIN] = windDownStartMin
    prefs[PREF_BEDTIME_MIN] = bedtimeMin
    prefs[PREF_PAUSE_REQUIRED_SECONDS] = pauseRequiredSeconds
    prefs[PREF_NOTIFICATIONS_ENABLED] = notificationsEnabled
    prefs[PREF_DIGEST_ENABLED] = digestEnabled
    prefs[PREF_LANGUAGE] = language
    prefs[PREF_LAST_DETECTOR_PACKAGE] = lastDetectorPackage
}

class SettingsStore(private val context: Context) {

    val flow: Flow<AppSettings> = context.appSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appSettingsDataStore.updateData { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences.toBuilder().apply { updated.writeTo(this) }.build()
        }
    }

    suspend fun setFriendKey(code: String) {
        val hash = if (code.isBlank()) {
            ""
        } else {
            MessageDigest.getInstance("SHA-256")
                .digest(code.trim().toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
        }
        context.appSettingsDataStore.edit { preferences ->
            preferences[PREF_FRIEND_KEY_HASH] = hash
        }
    }

    suspend fun clearFriendKey() {
        setFriendKey("")
    }
}
