package com.example.alarmqr

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var pickAudioBtn: Button
    private lateinit var setAlarmBtn: Button
    private lateinit var chosenLabel: TextView
    private lateinit var testQrBtn: Button
    private lateinit var resetQrBtn: Button
    private lateinit var audioStatus: ImageView
    private lateinit var qrStatus: ImageView

    private lateinit var nextAlarmInfo: TextView
    private lateinit var countdownText: TextView
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (updateCountdownUi()) {
                countdownHandler.postDelayed(this, 1000L)
            } else {
                stopCountdownUpdates()
            }
        }
    }
    private var countdownRunning = false

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

        setSupportActionBar(findViewById(R.id.toolbar))

        timePicker = findViewById(R.id.timePicker)
        pickAudioBtn = findViewById(R.id.pickAudioBtn)
        setAlarmBtn = findViewById(R.id.setAlarmBtn)
        chosenLabel = findViewById(R.id.chosenLabel)
        testQrBtn = findViewById(R.id.testQrBtn)
        resetQrBtn = findViewById(R.id.resetQrBtn)
        audioStatus = findViewById(R.id.audioStatus)
        qrStatus = findViewById(R.id.qrStatus)
        nextAlarmInfo = findViewById(R.id.nextAlarmInfo)
        countdownText = findViewById(R.id.countdownText)

        timePicker.setIs24HourView(false)

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

        setAlarmBtn.setOnClickListener { onAlarmButtonClick() }

        requestPostNotificationsIfNeeded()

        // Update button text when time changes
        timePicker.setOnTimeChangedListener { _, _, _ -> updateAlarmButton() }

        testQrBtn.setOnClickListener {
            val prefs = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_IS_RINGING, false)) {
                Toast.makeText(this, getString(R.string.qr_change_blocked), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, QrTestActivity::class.java))
        }

        resetQrBtn.setOnClickListener {
            val prefs = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_IS_RINGING, false)) {
                Toast.makeText(this, getString(R.string.qr_change_blocked), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit()
                .remove(KEY_QR_HASH)
                .remove(KEY_QR_READY)
                .apply()
            Toast.makeText(this, getString(R.string.qr_reset_done), Toast.LENGTH_SHORT).show()
            updateStatuses()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
        updateAlarmButton()
    }

    override fun onPause() {
        super.onPause()
        stopCountdownUpdates()
    }

    private fun scheduleAlarmForSelectedTime() {
        val cal = nextOccurrenceFromPicker()
        val audio = chosenAudio
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmService.EXTRA_AUDIO_URI, audio?.toString())
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(this, REQ_CODE_ALARM, intent, flags)

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val info = AlarmManager.AlarmClockInfo(cal.timeInMillis, showIntent)
        am.setAlarmClock(info, pi)

        getSharedPreferences("alarmqr", Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALARM_SCHEDULED, true)
            .putLong(KEY_ALARM_TIME_MILLIS, cal.timeInMillis)
            .apply()

        val remainingMillis = (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        Toast.makeText(
            this,
            getString(R.string.alarm_will_ring_in, formatCountdownClock(remainingMillis)),
            Toast.LENGTH_LONG
        ).show()
        updateAlarmButton()
    }

    private fun cancelScheduledAlarm() {
        val intent = Intent(this, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(this, REQ_CODE_ALARM, intent, flags)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        getSharedPreferences("alarmqr", Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALARM_SCHEDULED, false)
            .remove(KEY_ALARM_TIME_MILLIS)
            .apply()
        updateAlarmButton()
    }

    private fun onAlarmButtonClick() {
        val prefs = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean(KEY_IS_RINGING, false)
        if (isRinging) {
            Toast.makeText(this, getString(R.string.qr_change_blocked), Toast.LENGTH_LONG).show()
            return
        }
        val qrReady = prefs.getString(KEY_QR_HASH, null) != null || prefs.getBoolean(KEY_QR_READY, false)
        if (!qrReady) {
            Toast.makeText(this, "يرجى اختيار رمز QR أولاً", Toast.LENGTH_LONG).show()
            return
        }
        val scheduled = prefs.getBoolean(KEY_ALARM_SCHEDULED, false)
        if (scheduled) {
            cancelScheduledAlarm()
        } else {
            ensureExactAlarmPermissionIfNeeded { scheduleAlarmForSelectedTime() }
        }
    }

    private fun nextOccurrenceFromPicker(): Calendar {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val hour = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
        val minute = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal
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

    private fun ensureCountdownUpdates() {
        val shouldContinue = updateCountdownUi()
        if (shouldContinue) {
            if (!countdownRunning) {
                countdownRunning = true
                countdownHandler.postDelayed(countdownRunnable, 1000L)
            }
        } else {
            stopCountdownUpdates()
        }
    }

    private fun stopCountdownUpdates() {
        if (countdownRunning) {
            countdownHandler.removeCallbacks(countdownRunnable)
            countdownRunning = false
        }
    }

    private fun updateCountdownUi(): Boolean {
        val prefs = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean(KEY_IS_RINGING, false)
        val scheduled = prefs.getBoolean(KEY_ALARM_SCHEDULED, false)
        val targetMillis = prefs.getLong(KEY_ALARM_TIME_MILLIS, -1L)
        if (!scheduled || targetMillis <= 0L || isRinging) {
            nextAlarmInfo.visibility = View.GONE
            countdownText.visibility = View.GONE
            return false
        }
        val remaining = targetMillis - System.currentTimeMillis()
        if (remaining <= 0L) {
            nextAlarmInfo.visibility = View.GONE
            countdownText.visibility = View.GONE
            return false
        }
        val countdown = formatCountdownClock(remaining)
        nextAlarmInfo.visibility = View.VISIBLE
        nextAlarmInfo.text = getString(R.string.alarm_will_ring_in, countdown)
        countdownText.visibility = View.VISIBLE
        countdownText.text = countdown
        return true
    }

    private fun formatCountdownClock(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale("ar"), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        private const val KEY_AUDIO_URI = "audio_uri"
        const val KEY_QR_READY = "qr_ready"
        const val KEY_QR_HASH = "qr_hash"
        const val KEY_IS_RINGING = "is_ringing"
        const val KEY_ALARM_SCHEDULED = "alarm_scheduled"
        const val KEY_ALARM_TIME_MILLIS = "alarm_time_ms"
        const val REQ_CODE_ALARM = 10001
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 100)
            }
        }
    }

    private fun updateStatuses() {
        val prefs = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
        val audioExists = prefs.getString(KEY_AUDIO_URI, null) != null
        val qrReady = prefs.getString(KEY_QR_HASH, null) != null || prefs.getBoolean(KEY_QR_READY, false)

        audioStatus.setImageResource(if (audioExists) R.drawable.dot_green else R.drawable.dot_red)
        qrStatus.setImageResource(if (qrReady) R.drawable.dot_green else R.drawable.dot_red)
    }

    private fun updateAlarmButton() {
        val prefs = getSharedPreferences("alarmqr", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean(KEY_IS_RINGING, false)
        val timeStr = formatSelectedTime()
        testQrBtn.isEnabled = !isRinging
        resetQrBtn.isEnabled = !isRinging
        if (isRinging) {
            setAlarmBtn.isEnabled = false
            setAlarmBtn.text = "يَرِنّ — امسح QR"
            ensureCountdownUpdates()
            return
        }
        setAlarmBtn.isEnabled = true
        val scheduled = prefs.getBoolean(KEY_ALARM_SCHEDULED, false)
        setAlarmBtn.text = if (scheduled) "إيقاف $timeStr" else "تشغيل $timeStr"
        ensureCountdownUpdates()
    }

    private fun formatSelectedTime(): String {
        val hour = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
        val minute = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        val fmt = java.text.SimpleDateFormat("h:mm a", Locale("ar"))
        return fmt.format(cal.time)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
