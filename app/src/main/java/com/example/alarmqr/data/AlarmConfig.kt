package com.example.alarmqr.data

data class AlarmConfig(
    val alarmTimeMillis: Long?,
    val ringtoneUri: String?,
    val qrPayload: String?,
    val isActive: Boolean,
    val isEnabled: Boolean,
    val pinCodeHash: String?
) {
    fun hasCompleteSetup(): Boolean = alarmTimeMillis != null && ringtoneUri != null && qrPayload != null
}
