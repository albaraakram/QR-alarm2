package com.example.alarmqr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val audioUri = intent.getStringExtra(AlarmService.EXTRA_AUDIO_URI)
        val service = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_AUDIO_URI, audioUri)
            action = AlarmService.ACTION_START
        }
        ContextCompat.startForegroundService(context, service)
    }
}

