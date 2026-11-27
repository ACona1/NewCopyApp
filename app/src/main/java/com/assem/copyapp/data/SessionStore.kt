package com.assem.copyapp.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appSessionDataStore by preferencesDataStore(name = "app_session")

private const val KEY_ACTIVE = "active"
private const val KEY_START_WALL = "startWall"
private const val KEY_END_WALL = "endWall"
private const val KEY_START_MONO = "startMono"
private const val KEY_MODE = "mode"
private const val KEY_BLOCKED = "blocked"
private const val KEY_STRICT = "strict"
private const val KEY_PROFILE_ID = "profileId"
private const val KEY_PAUSE_UNTIL_MONO = "pauseUntilMono"
private const val KEY_BREAKS = "breaks"
private const val KEY_WALL_TRUSTED = "wallTrusted"
private const val KEY_SESSION_ID = "sessionId"

private val PREF_ACTIVE = booleanPreferencesKey(KEY_ACTIVE)
private val PREF_START_WALL = longPreferencesKey(KEY_START_WALL)
private val PREF_END_WALL = longPreferencesKey(KEY_END_WALL)
private val PREF_START_MONO = longPreferencesKey(KEY_START_MONO)
private val PREF_MODE = stringPreferencesKey(KEY_MODE)
private val PREF_BLOCKED = stringSetPreferencesKey(KEY_BLOCKED)
private val PREF_STRICT = booleanPreferencesKey(KEY_STRICT)
private val PREF_PROFILE_ID = longPreferencesKey(KEY_PROFILE_ID)
private val PREF_PAUSE_UNTIL_MONO = longPreferencesKey(KEY_PAUSE_UNTIL_MONO)
private val PREF_BREAKS = intPreferencesKey(KEY_BREAKS)
private val PREF_WALL_TRUSTED = booleanPreferencesKey(KEY_WALL_TRUSTED)
private val PREF_SESSION_ID = longPreferencesKey(KEY_SESSION_ID)

data class SessionState(
    val active: Boolean = false,
    val startWall: Long = 0L,
    val endWall: Long = 0L,
    val startMono: Long = 0L,
    val mode: SessionMode = SessionMode.SOFT,
    val blocked: Set<String> = emptySet(),
    val strict: Boolean = false,
    val profileId: Long = 0L,
    val pauseUntilMono: Long = 0L,
    val breaks: Int = 0,
    val wallTrusted: Boolean = true,
    val sessionId: Long = 0L
)

private fun Preferences.toSessionState(): SessionState {
    val storedMode = this[PREF_MODE] ?: SessionMode.SOFT.name
    return SessionState(
        active = this[PREF_ACTIVE] ?: false,
        startWall = this[PREF_START_WALL] ?: 0L,
        endWall = this[PREF_END_WALL] ?: 0L,
        startMono = this[PREF_START_MONO] ?: 0L,
        mode = runCatching { SessionMode.valueOf(storedMode) }.getOrDefault(SessionMode.SOFT),
        blocked = this[PREF_BLOCKED] ?: emptySet(),
        strict = this[PREF_STRICT] ?: false,
        profileId = this[PREF_PROFILE_ID] ?: 0L,
        pauseUntilMono = this[PREF_PAUSE_UNTIL_MONO] ?: 0L,
        breaks = this[PREF_BREAKS] ?: 0,
        wallTrusted = this[PREF_WALL_TRUSTED] ?: true,
        sessionId = this[PREF_SESSION_ID] ?: 0L
    )
}

private fun SessionState.writeTo(prefs: MutablePreferences) {
    prefs[PREF_ACTIVE] = active
    prefs[PREF_START_WALL] = startWall
    prefs[PREF_END_WALL] = endWall
    prefs[PREF_START_MONO] = startMono
    prefs[PREF_MODE] = mode.name
    prefs[PREF_BLOCKED] = blocked
    prefs[PREF_STRICT] = strict
    prefs[PREF_PROFILE_ID] = profileId
    prefs[PREF_PAUSE_UNTIL_MONO] = pauseUntilMono
    prefs[PREF_BREAKS] = breaks
    prefs[PREF_WALL_TRUSTED] = wallTrusted
    prefs[PREF_SESSION_ID] = sessionId
}

class SessionStore(private val context: Context) {

    val flow: Flow<SessionState> = context.appSessionDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSessionState() }

    suspend fun start(state: SessionState) {
        context.appSessionDataStore.updateData { preferences ->
            preferences.toBuilder().apply { state.writeTo(this) }.build()
        }
    }

    suspend fun update(transform: (SessionState) -> SessionState) {
        context.appSessionDataStore.updateData { preferences ->
            val updated = transform(preferences.toSessionState())
            preferences.toBuilder().apply { updated.writeTo(this) }.build()
        }
    }

    suspend fun clear() {
        context.appSessionDataStore.updateData { preferences ->
            preferences.toBuilder().apply { SessionState().writeTo(this) }.build()
        }
    }

    suspend fun pauseFor(monoUntil: Long) {
        context.appSessionDataStore.updateData { preferences ->
            val updated = preferences.toSessionState().copy(pauseUntilMono = monoUntil)
            preferences.toBuilder().apply { updated.writeTo(this) }.build()
        }
    }
}
