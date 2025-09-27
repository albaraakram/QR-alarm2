package com.example.alarmqr

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.alarmqr.data.AlarmPreferences
import com.example.alarmqr.databinding.ActivityAlarmBinding
import com.example.alarmqr.service.AlarmService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var preferences: AlarmPreferences
    private var storedQrPayload: String? = null

    private val qrVerificationLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null && contents == storedQrPayload) {
            stopAlarm()
        } else {
            Toast.makeText(this, "—„“ €Ì— „ÿ«»ﬁ", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        preferences = AlarmPreferences(applicationContext)

        binding.scanButton.setOnClickListener { launchScanner() }

        lifecycleScope.launch {
            val config = preferences.alarmConfig.first()
            storedQrPayload = config.qrPayload
        }
    }

    private fun launchScanner() {
        if (storedQrPayload.isNullOrEmpty()) {
            Toast.makeText(this, "·« ÌÊÃœ —„“ „Õ›ÊŸ", Toast.LENGTH_SHORT).show()
            return
        }
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_to_stop))
            setBeepEnabled(false)
        }
        qrVerificationLauncher.launch(options)
    }

    private fun stopAlarm() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(intent)
        Toast.makeText(this, getString(R.string.alarm_stopped), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onBackPressed() {
        // „‰⁄ «·Œ—ÊÃ √À‰«¡ «·—‰Ì‰
    }
}
