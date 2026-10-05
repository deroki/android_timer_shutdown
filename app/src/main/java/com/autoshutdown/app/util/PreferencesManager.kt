package com.autoshutdown.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shutdown_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_HOURS = intPreferencesKey("shutdown_hours")
        val KEY_MINUTES = intPreferencesKey("shutdown_minutes")
        val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val KEY_SECONDS = intPreferencesKey("shutdown_seconds")
        val KEY_AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val KEY_SIMULATION_MODE = booleanPreferencesKey("simulation_mode")
        val KEY_TARGET_SHUTDOWN_MILLIS = longPreferencesKey("target_shutdown_millis")
        val KEY_SCREEN_OFF_TIMESTAMP = longPreferencesKey("screen_off_timestamp")
    }

    val hoursFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_HOURS] ?: 1 // Default to 1 hour
    }

    val minutesFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MINUTES] ?: 0 // Default to 0 minutes
    }

    val secondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_SECONDS] ?: 0 // Default to 0 seconds
    }

    val isServiceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVICE_ENABLED] ?: false
    }

    val autoStartOnBootFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_START_BOOT] ?: true
    }

    val isSimulationModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SIMULATION_MODE] ?: false
    }

    val targetShutdownMillisFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_TARGET_SHUTDOWN_MILLIS] ?: 0L
    }

    val screenOffTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_SCREEN_OFF_TIMESTAMP] ?: 0L
    }

    suspend fun setHours(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HOURS] = hours.coerceIn(0, 72)
        }
    }

    suspend fun setMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MINUTES] = minutes.coerceIn(0, 59)
        }
    }

    suspend fun setSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SECONDS] = seconds.coerceIn(0, 59)
        }
    }

    suspend fun setSimulationMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SIMULATION_MODE] = enabled
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setAutoStartOnBoot(autoStart: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_START_BOOT] = autoStart
        }
    }

    suspend fun setTargetShutdownMillis(targetMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TARGET_SHUTDOWN_MILLIS] = targetMillis
        }
    }

    suspend fun setScreenOffTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SCREEN_OFF_TIMESTAMP] = timestamp
        }
    }

    fun getTotalDurationMillis(hours: Int, minutes: Int, seconds: Int = 0): Long {
        val safeHours = hours.coerceAtLeast(0)
        val safeMinutes = minutes.coerceAtLeast(0)
        val safeSeconds = seconds.coerceAtLeast(0)
        val total = (safeHours * 3600L + safeMinutes * 60L + safeSeconds) * 1000L
        return if (total <= 0L) 10_000L else total
    }
}

