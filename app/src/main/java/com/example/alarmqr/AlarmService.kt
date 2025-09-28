package com.example.alarmqr

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var volumeBoostRunnable: Runnable? = null
    private var autoStopRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm(intent.getStringExtra(EXTRA_AUDIO_URI))
            ACTION_STOP -> stopAlarm()
        }
        return START_STICKY
    }

    private fun startAlarm(audioUriStr: String?) {
        // Mark ringing state
        try {
            getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_IS_RINGING, true)
                .putBoolean(MainActivity.KEY_ALARM_SCHEDULED, false)
                .remove(MainActivity.KEY_ALARM_TIME_MILLIS)
                .apply()
        } catch (_: Exception) {}
        // Request audio focus for alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val aa = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val afr = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(aa)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioManager.requestAudioFocus(afr)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}

        val fullScreenIntent = Intent(this, RingingActivity::class.java)
        val fsFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, fsFlags)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("جرس المنبّه يعمل")
            .setContentText("لإيقاف الجرس امسح أي رمز QR")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)

        forceMaxAlarmVolume()
        startVolumeBoostLoop()

        // Auto-stop after 25 minutes if not stopped by QR
        autoStopRunnable = Runnable { stopAlarm() }
        handler.postDelayed(autoStopRunnable!!, MAX_RING_MS)

        val uri = audioUriStr?.let { Uri.parse(it) }
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            try {
                if (uri != null) {
                    setDataSource(this@AlarmService, uri)
                } else {
                    val def = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    setDataSource(this@AlarmService, def)
                }
                isLooping = true
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun forceMaxAlarmVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
    }

    private fun startVolumeBoostLoop() {
        stopVolumeBoostLoop()
        volumeBoostRunnable = object : Runnable {
            override fun run() {
                forceMaxAlarmVolume()
                handler.postDelayed(this, 600)
            }
        }
        handler.post(volumeBoostRunnable!!)
    }

    private fun stopVolumeBoostLoop() {
        volumeBoostRunnable?.let { handler.removeCallbacks(it) }
        volumeBoostRunnable = null
    }

    private fun stopAlarm() {
        try {
            getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.KEY_IS_RINGING, false).apply()
        } catch (_: Exception) {}
        stopVolumeBoostLoop()
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.KEY_IS_RINGING, false).apply()
        } catch (_: Exception) {}
        stopVolumeBoostLoop()
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIF_ID = 1001
        const val EXTRA_AUDIO_URI = "extra_audio_uri"
        const val ACTION_START = "com.example.alarmqr.action.START"
        const val ACTION_STOP = "com.example.alarmqr.action.STOP"
        const val MAX_RING_MS = 25L * 60L * 1000L
    }
}
