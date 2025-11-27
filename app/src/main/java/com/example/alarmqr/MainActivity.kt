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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.alarmqr.data.AlarmPreferences
import com.example.alarmqr.databinding.ActivityMainBinding
import com.example.alarmqr.databinding.DialogSetPinBinding
import com.example.alarmqr.databinding.DialogVerifyPinBinding
import com.example.alarmqr.scheduler.AlarmScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private fun formatDurationUntil(triggerAt: Long): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val diff = if (triggerAt > now) triggerAt - now else 0L
        val hours = (diff / 3_600_000L).toInt()
        val minutes = ((diff % 3_600_000L) / 60_000L).toInt()
        return hours to minutes
    }

    private fun nextOccurrenceFromStoredOrNow(): Long? {
        val stored = selectedAlarmTimeMillis ?: return null
        val calStored = java.util.Calendar.getInstance().apply { timeInMillis = stored }
        val h = calStored.get(java.util.Calendar.HOUR_OF_DAY)
        val m = calStored.get(java.util.Calendar.MINUTE)
        return resolveNextTriggerMillis(h, m)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AlarmPreferences
    private lateinit var scheduler: AlarmScheduler

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private var selectedAlarmTimeMillis: Long? = null
    private var selectedRingtoneUri: Uri? = null
    private var storedQrPayload: String? = null
    private var isAlarmActive: Boolean = false
    private var isAlarmEnabled: Boolean = false
    private var hasConfiguredPin: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, getString(R.string.qr_saved), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                preferences.updateAlarm(
                    timeMillis = selectedAlarmTimeMillis,
                    ringtoneUri = selectedRingtoneUri?.toString(),
                    qrPayload = storedQrPayload
                )
            }
        } else {
            Toast.makeText(this, getString(R.string.qr_not_captured), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toggle enable/disable alarm
        binding.alarmEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (isAlarmActive) {
                binding.alarmEnabledSwitch.isChecked = true
                return@setOnCheckedChangeListener
            }
            if (checked) {
                val triggerAt = nextOccurrenceFromStoredOrNow()
                val uri = selectedRingtoneUri
                val qr = storedQrPayload
                if (triggerAt == null || uri == null || qr.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.alarm_requires_setup), Toast.LENGTH_LONG).show()
                    binding.alarmEnabledSwitch.isChecked = false
                } else {
                    lifecycleScope.launch {
                        preferences.updateAlarm(triggerAt, uri.toString(), qr)
                        scheduler.schedule(triggerAt)
                        preferences.setEnabled(true)
                        isAlarmEnabled = true
                        val (h, m) = formatDurationUntil(triggerAt)
                        Toast.makeText(this@MainActivity, getString(R.string.alarm_in_time, h, m), Toast.LENGTH_LONG).show()
                        updateUi()
                    }
                }
            } else {
                scheduler.cancel()
                lifecycleScope.launch { preferences.setEnabled(false) }
                isAlarmEnabled = false
                updateUi()
                Toast.makeText(this, getString(R.string.alarm_disabled_toast), Toast.LENGTH_SHORT).show()
            }
        }


        preferences = AlarmPreferences(applicationContext)
        scheduler = AlarmScheduler(applicationContext)

        binding.setTimeButton.setOnClickListener { openTimePicker() }
        binding.selectRingtoneButton.setOnClickListener { pickRingtone() }
        binding.registerQrButton.setOnClickListener { startQrEnrollment() }
        binding.saveButton.setOnClickListener { saveAlarm() }
        binding.setPinButton.setOnClickListener { showPinSetupDialog() }
        binding.deleteAppButton.setOnClickListener { attemptAppDeletion() }

        lifecycleScope.launch {
            preferences.alarmConfig.collectLatest { config ->
                selectedAlarmTimeMillis = config.alarmTimeMillis
                selectedRingtoneUri = config.ringtoneUri?.toUri()
                storedQrPayload = config.qrPayload
                isAlarmActive = config.isActive
                isAlarmEnabled = config.isEnabled
                hasConfiguredPin = !config.pinCodeHash.isNullOrEmpty()
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

        TimePickerDialog(this, listener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun pickRingtone() {
        if (isAlarmActive) return
        ringtonePickerLauncher.launch(arrayOf("audio/*"))
    }

    private fun startQrEnrollment() {
        if (isAlarmActive) return
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.qr_prompt))
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
            preferences.setEnabled(true)
            scheduler.schedule(triggerAt)
            isAlarmEnabled = true
            val (hours, minutes) = formatDurationUntil(triggerAt)
            Toast.makeText(this@MainActivity, getString(R.string.alarm_in_time, hours, minutes), Toast.LENGTH_LONG).show()
            binding.alarmEnabledSwitch.isChecked = true
            updateUi()
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
        val enabledInputs = !isAlarmActive
        binding.setTimeButton.isEnabled = enabledInputs
        binding.selectRingtoneButton.isEnabled = enabledInputs
        binding.registerQrButton.isEnabled = enabledInputs
        binding.saveButton.isEnabled = enabledInputs

        binding.alarmEnabledSwitch.isEnabled = !isAlarmActive
        binding.alarmEnabledSwitch.isChecked = isAlarmEnabled

        val next = if (isAlarmEnabled) nextOccurrenceFromStoredOrNow() else null
        binding.nextAlarmInfo.text = next?.let {
            val (hours, minutes) = formatDurationUntil(it)
            getString(R.string.alarm_in_time, hours, minutes)
        } ?: ""

        updatePinUi()
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

    private fun updatePinUi() {
        binding.pinStatusValue.text = getString(
            if (hasConfiguredPin) R.string.pin_status_set else R.string.pin_status_not_set
        )
        binding.deleteAppButton.isEnabled = hasConfiguredPin
    }

    private fun showPinSetupDialog() {
        val dialogBinding = DialogSetPinBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pin_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_pin_button, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.newPinLayout.error = null
                dialogBinding.confirmPinLayout.error = null
                val pin = dialogBinding.newPinInput.text?.toString()?.trim().orEmpty()
                val confirm = dialogBinding.confirmPinInput.text?.toString()?.trim().orEmpty()
                if (!isPinFormatValid(pin)) {
                    dialogBinding.newPinLayout.error = getString(R.string.pin_length_error)
                    return@setOnClickListener
                }
                if (pin != confirm) {
                    dialogBinding.confirmPinLayout.error = getString(R.string.pin_mismatch_error)
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    preferences.savePin(pin)
                    hasConfiguredPin = true
                    updatePinUi()
                    Toast.makeText(this@MainActivity, getString(R.string.pin_saved_toast), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun attemptAppDeletion() {
        if (!hasConfiguredPin) {
            Toast.makeText(this, getString(R.string.pin_required_for_deletion), Toast.LENGTH_LONG).show()
            return
        }
        showPinVerificationDialog()
    }

    private fun showPinVerificationDialog() {
        val dialogBinding = DialogVerifyPinBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pin_verify_title)
            .setMessage(R.string.delete_app_info)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.delete_app_confirmation_button, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.pinLayout.error = null
                val candidate = dialogBinding.pinInput.text?.toString()?.trim().orEmpty()
                if (!isPinFormatValid(candidate)) {
                    dialogBinding.pinLayout.error = getString(R.string.pin_length_error)
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    val valid = preferences.isPinValid(candidate)
                    if (valid) {
                        dialog.dismiss()
                        launchUninstallIntent()
                    } else {
                        dialogBinding.pinLayout.error = getString(R.string.pin_invalid_error)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun isPinFormatValid(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    private fun launchUninstallIntent() {
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_DELETE, packageUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}


