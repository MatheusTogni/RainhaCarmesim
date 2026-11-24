package com.rainhacarmesim.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Preferências simples do app (nenhum dado pessoal ou credencial). */
class SettingsStore(private val context: Context) {
    private object Keys {
        val autoScan = booleanPreferencesKey("auto_scan")
        val autoScanMinutes = intPreferencesKey("auto_scan_minutes")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val speedTestWarningAccepted = booleanPreferencesKey("speed_warning_ok")
    }

    companion object {
        val INTERVAL_OPTIONS = listOf(1, 2, 5, 10)
        const val DEFAULT_INTERVAL = 2
    }

    val autoScanEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.autoScan] ?: true }
    val autoScanMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.autoScanMinutes] ?: DEFAULT_INTERVAL }
    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { it[Keys.lastUpdateCheck] ?: 0L }
    val speedTestWarningAccepted: Flow<Boolean> = context.dataStore.data.map { it[Keys.speedTestWarningAccepted] ?: false }

    suspend fun setAutoScanEnabled(value: Boolean) = context.dataStore.edit { it[Keys.autoScan] = value }
    suspend fun setAutoScanMinutes(value: Int) = context.dataStore.edit { it[Keys.autoScanMinutes] = value }
    suspend fun setLastUpdateCheck(value: Long) = context.dataStore.edit { it[Keys.lastUpdateCheck] = value }
    suspend fun setSpeedTestWarningAccepted(value: Boolean) = context.dataStore.edit { it[Keys.speedTestWarningAccepted] = value }
}
