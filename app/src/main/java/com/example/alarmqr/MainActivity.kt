package com.example.alarmqr

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var pickAudioBtn: Button
    private lateinit var setAlarmBtn: Button
    private lateinit var chosenLabel: TextView

    private var chosenAudio: Uri? = null

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    val takeFlags = (result.data?.flags ?: 0) and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (_: Exception) {}
                chosenAudio = uri
                getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_AUDIO_URI, uri.toString())
                    .apply()
                chosenLabel.text = getString(R.string.audio_chosen)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timePicker = findViewById(R.id.timePicker)
        pickAudioBtn = findViewById(R.id.pickAudioBtn)
        setAlarmBtn = findViewById(R.id.setAlarmBtn)
        chosenLabel = findViewById(R.id.chosenLabel)

        timePicker.setIs24HourView(true)

        val saved = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
            .getString(KEY_AUDIO_URI, null)
        if (saved != null) {
            chosenAudio = Uri.parse(saved)
            chosenLabel.text = getString(R.string.audio_chosen)
        }

        pickAudioBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*"))
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pickAudioLauncher.launch(intent)
        }

        setAlarmBtn.setOnClickListener {
            ensureExactAlarmPermissionIfNeeded {
                scheduleAlarm()
            }
        }

        requestPostNotificationsIfNeeded()
    }

    private fun scheduleAlarm() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val hour = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
        val minute = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val audio = chosenAudio
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmService.EXTRA_AUDIO_URI, audio?.toString())
        }

        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(this, requestCode, intent, flags)

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = cal.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }

        Toast.makeText(this, getString(R.string.alarm_set), Toast.LENGTH_LONG).show()
    }

    private fun ensureExactAlarmPermissionIfNeeded(onReady: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(this, getString(R.string.need_exact_alarm_perm), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }
        onReady()
    }

    companion object {
        private const val KEY_AUDIO_URI = "audio_uri"
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 100)
            }
        }
    }
}
