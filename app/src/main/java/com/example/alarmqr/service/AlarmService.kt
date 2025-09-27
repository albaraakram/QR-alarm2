package com.example.alarmqr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.example.alarmqr.AlarmActivity
import com.example.alarmqr.R
import com.example.alarmqr.data.AlarmPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var alarmPreferences: AlarmPreferences
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var volumeGuard: VolumeGuard
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        alarmPreferences = AlarmPreferences(applicationContext)
        volumeGuard = VolumeGuard(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarmAndSelf()
            return START_NOT_STICKY
        }

        if (isRunning) {
            return START_STICKY
        }

        serviceScope.launch {
            val config = alarmPreferences.alarmConfig.first()
            if (!config.hasCompleteSetup()) {
                stopSelf()
                return@launch
            }

            startForeground(NOTIFICATION_ID, buildNotification())
            volumeGuard.start()
            startPlayback(config.ringtoneUri!!)
            alarmPreferences.setAlarmActive(true)
            isRunning = true
        }

        return START_STICKY
    }

    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun buildAndRegisterChannel(): String {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager = requireNotNull(getSystemService())
            val channel = NotificationChannel(
                channelId,
                getString(R.string.alarm_service_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarm_notification_body)
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    private suspend fun buildNotification(): Notification {
        val channelId = withContext(Dispatchers.IO) { buildAndRegisterChannel() }
        val launchIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_STOP_ALARM }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(getString(R.string.alarm_notification_body))
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(R.drawable.ic_alarm_notification, getString(R.string.stop_alarm), stopPendingIntent)
            .build()
    }

    private suspend fun startPlayback(ringtoneUri: String) {
        val uri = Uri.parse(ringtoneUri)
        withContext(Dispatchers.IO) {
            stopPlayback()
            val audioManager: AudioManager? = getSystemService()
            audioManager?.requestAudioFocus(
                { },
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN
            )
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun stopAlarmAndSelf() {
        cleanupResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupResources() {
        if (!isRunning) {
            return
        }
        serviceScope.launch {
            alarmPreferences.setAlarmActive(false)
        }
        stopPlayback()
        volumeGuard.stop()
        isRunning = false
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
    }

    companion object {
        private const val CHANNEL_ID = "qr_alarm_channel"
        private const val NOTIFICATION_ID = 991
        const val ACTION_STOP_ALARM = "com.example.alarmqr.action.STOP_ALARM"
    }
}
