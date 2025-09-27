package com.example.alarmqr

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.alarmqr.data.AlarmPreferences
import com.example.alarmqr.databinding.ActivityMainBinding
import com.example.alarmqr.scheduler.AlarmScheduler
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AlarmPreferences
    private lateinit var scheduler: AlarmScheduler

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var selectedAlarmTimeMillis: Long? = null
    private var selectedRingtoneUri: Uri? = null
    private var storedQrPayload: String? = null
    private var isAlarmActive: Boolean = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "يجب منح الصلاحيات المطلوبة لعمل التطبيق", Toast.LENGTH_LONG).show()
        }
    }

    private val ringtonePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedRingtoneUri = uri
            binding.ringtoneValue.text = resolveRingtoneTitle(uri)
        }
    }

    private val qrEnrollmentLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            storedQrPayload = contents
            Toast.makeText(this, "تم حفظ رمز QR", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                preferences.updateAlarm(
                    timeMillis = selectedAlarmTimeMillis,
                    ringtoneUri = selectedRingtoneUri?.toString(),
                    qrPayload = storedQrPayload
                )
            }
        } else {
            Toast.makeText(this, "لم يتم التقاط رمز", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AlarmPreferences(applicationContext)
        scheduler = AlarmScheduler(applicationContext)

        binding.setTimeButton.setOnClickListener { openTimePicker() }
        binding.selectRingtoneButton.setOnClickListener { pickRingtone() }
        binding.registerQrButton.setOnClickListener { startQrEnrollment() }
        binding.saveButton.setOnClickListener { saveAlarm() }

        lifecycleScope.launch {
            preferences.alarmConfig.collectLatest { config ->
                selectedAlarmTimeMillis = config.alarmTimeMillis
                selectedRingtoneUri = config.ringtoneUri?.toUri()
                storedQrPayload = config.qrPayload
                isAlarmActive = config.isActive
                updateUi()
            }
        }

        ensurePermissions()
    }

    private fun ensurePermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_AUDIO
            permissions += Manifest.permission.POST_NOTIFICATIONS
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openTimePicker() {
        if (isAlarmActive) return
        val calendar = Calendar.getInstance()
        selectedAlarmTimeMillis?.let { calendar.timeInMillis = it }

        val listener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            val scheduledTime = resolveNextTriggerMillis(hourOfDay, minute)
            selectedAlarmTimeMillis = scheduledTime
            binding.timeValue.text = timeFormat.format(Calendar.getInstance().apply { timeInMillis = scheduledTime }.time)
        }

        TimePickerDialog(this, listener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun pickRingtone() {
        if (isAlarmActive) return
        ringtonePickerLauncher.launch(arrayOf("audio/*"))
    }

    private fun startQrEnrollment() {
        if (isAlarmActive) return
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("وجّه الكاميرا نحو رمز QR")
            setBeepEnabled(false)
        }
        qrEnrollmentLauncher.launch(options)
    }

    private fun saveAlarm() {
        if (storedQrPayload.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.missing_qr), Toast.LENGTH_LONG).show()
            return
        }
        val ringtoneUri = selectedRingtoneUri
        if (ringtoneUri == null) {
            Toast.makeText(this, getString(R.string.missing_ringtone), Toast.LENGTH_LONG).show()
            return
        }
        val triggerAt = selectedAlarmTimeMillis ?: run {
            Toast.makeText(this, getString(R.string.set_alarm), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            preferences.updateAlarm(triggerAt, ringtoneUri.toString(), storedQrPayload)
            scheduler.schedule(triggerAt)
            Toast.makeText(this@MainActivity, getString(R.string.alarm_set), Toast.LENGTH_SHORT).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ensureExactAlarmPermission()
        }
    }

    private fun ensureExactAlarmPermission() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            startActivity(intent)
        }
    }

    private fun updateUi() {
        binding.timeValue.text = selectedAlarmTimeMillis?.let { millis ->
            timeFormat.format(Calendar.getInstance().apply { timeInMillis = millis }.time)
        } ?: "--:--"

        binding.ringtoneValue.text = selectedRingtoneUri?.let { resolveRingtoneTitle(it) } ?: ""

        binding.lockMessage.visibility = if (isAlarmActive) android.view.View.VISIBLE else android.view.View.GONE
        val enabled = !isAlarmActive
        binding.setTimeButton.isEnabled = enabled
        binding.selectRingtoneButton.isEnabled = enabled
        binding.registerQrButton.isEnabled = enabled
        binding.saveButton.isEnabled = enabled
    }

    private fun resolveNextTriggerMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun resolveRingtoneTitle(uri: Uri): String {
        val ringtone = RingtoneManager.getRingtone(this, uri)
        return ringtone?.getTitle(this) ?: uri.lastPathSegment.orEmpty()
    }
}

