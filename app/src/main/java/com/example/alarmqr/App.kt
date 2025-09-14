package com.example.alarmqr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import com.google.android.material.color.DynamicColors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply Material 3 dynamic color on Android 12+
        try { DynamicColors.applyToActivitiesIfAvailable(this) } catch (_: Exception) {}
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = AlarmService.CHANNEL_ID
            val name = "منبّه"
            val descriptionText = "قناة تنبيهات المنبّه"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                setSound(null, null) // الصوت يُدار بواسطة MediaPlayer
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(false)
                }
                setBypassDnd(true)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(null, attrs)
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
