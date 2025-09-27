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
import kotlinx.coroutines.flow.map

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarm_prefs")

class AlarmPreferences(private val context: Context) {

    private val alarmTimeKey = longPreferencesKey("alarm_time")
    private val ringtoneUriKey = stringPreferencesKey("ringtone_uri")
    private val qrPayloadKey = stringPreferencesKey("qr_payload")
    private val alarmActiveKey = booleanPreferencesKey("alarm_active")
    private val alarmEnabledKey = booleanPreferencesKey("alarm_enabled")

    val alarmConfig: Flow<AlarmConfig> = context.alarmDataStore.data.map { prefs ->
        AlarmConfig(
            alarmTimeMillis = prefs[alarmTimeKey],
            ringtoneUri = prefs[ringtoneUriKey],
            qrPayload = prefs[qrPayloadKey],
            isActive = prefs[alarmActiveKey] ?: false,
            isEnabled = prefs[alarmEnabledKey] ?: false
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
}
