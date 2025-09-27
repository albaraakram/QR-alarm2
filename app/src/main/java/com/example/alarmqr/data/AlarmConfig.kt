package com.example.alarmqr.data

data class AlarmConfig(
    val alarmTimeMillis: Long?,
    val ringtoneUri: String?,
    val qrPayload: String?,
    val isActive: Boolean
) {
    fun hasCompleteSetup(): Boolean = alarmTimeMillis != null && ringtoneUri != null && qrPayload != null
}
