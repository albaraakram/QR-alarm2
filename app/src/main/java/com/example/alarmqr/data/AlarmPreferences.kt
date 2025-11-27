package com.example.alarmqr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarm_prefs")

class AlarmPreferences(private val context: Context) {

    private val alarmTimeKey = longPreferencesKey("alarm_time")
    private val ringtoneUriKey = stringPreferencesKey("ringtone_uri")
    private val qrPayloadKey = stringPreferencesKey("qr_payload")
    private val alarmActiveKey = booleanPreferencesKey("alarm_active")
    private val alarmEnabledKey = booleanPreferencesKey("alarm_enabled")
    private val pinCodeKey = stringPreferencesKey("pin_code_hash")

    val alarmConfig: Flow<AlarmConfig> = context.alarmDataStore.data.map { prefs ->
        AlarmConfig(
            alarmTimeMillis = prefs[alarmTimeKey],
            ringtoneUri = prefs[ringtoneUriKey],
            qrPayload = prefs[qrPayloadKey],
            isActive = prefs[alarmActiveKey] ?: false,
            isEnabled = prefs[alarmEnabledKey] ?: false,
            pinCodeHash = prefs[pinCodeKey]
        )
    }

    suspend fun updateAlarm(timeMillis: Long?, ringtoneUri: String?, qrPayload: String?) {
        context.alarmDataStore.edit { prefs ->
            if (timeMillis != null) {
                prefs[alarmTimeKey] = timeMillis
            }
            if (ringtoneUri != null) {
                prefs[ringtoneUriKey] = ringtoneUri
            }
            if (qrPayload != null) {
                prefs[qrPayloadKey] = qrPayload
            }
        }
    }

    suspend fun clearAlarm() {
        context.alarmDataStore.edit { prefs ->
            prefs.remove(alarmTimeKey)
            prefs.remove(ringtoneUriKey)
            prefs.remove(qrPayloadKey)
            prefs[alarmActiveKey] = false
            prefs[alarmEnabledKey] = false
            prefs.remove(pinCodeKey)
        }
    }

    suspend fun setAlarmActive(active: Boolean) {
        context.alarmDataStore.edit { prefs ->
            prefs[alarmActiveKey] = active
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.alarmDataStore.edit { prefs ->
            prefs[alarmEnabledKey] = enabled
        }
    }

    suspend fun savePin(pin: String) {
        context.alarmDataStore.edit { prefs ->
            prefs[pinCodeKey] = hashPin(pin)
        }
    }

    suspend fun clearPin() {
        context.alarmDataStore.edit { prefs ->
            prefs.remove(pinCodeKey)
        }
    }

    suspend fun isPinValid(candidate: String): Boolean {
        val storedHash = context.alarmDataStore.data.map { it[pinCodeKey] }.firstOrNull()
        return storedHash != null && storedHash == hashPin(candidate)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hash.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
