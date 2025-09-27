package com.example.alarmqr.service

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.getSystemService

class VolumeGuard(private val context: Context) {

    private val audioManager: AudioManager? = context.getSystemService()
    private val handler = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            setAlarmStreamToMax()
        }
    }

    fun start() {
        setAlarmStreamToMax()
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer
        )
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(observer)
    }

    private fun setAlarmStreamToMax() {
        audioManager?.let { manager ->
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            manager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
    }
}
