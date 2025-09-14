package com.example.alarmqr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class RingingActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var hintText: TextView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var handled = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "لن يتوقف الجرس بدون إذن الكاميرا", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ringing)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        previewView = findViewById(R.id.previewView)
        hintText = findViewById(R.id.hintText)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    val scanner = BarcodeScanning.getClient()
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !handled) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    if (!handled && barcodes.isNotEmpty()) {
                                        val prefs = getSharedPreferences("alarmqr", android.content.Context.MODE_PRIVATE)
                                        val savedHash = prefs.getString(MainActivity.KEY_QR_HASH, null)
                                        val value = barcodes.firstOrNull()?.rawValue
                                        if (savedHash != null && value != null) {
                                            val hash = HashUtil.sha256(value)
                                            if (hash == savedHash) {
                                                handled = true
                                                stopAlarmAndFinish()
                                            } else {
                                                hintText.text = "رمز غير مطابق — امسح الرمز المحفوظ"
                                            }
                                        } else {
                                            // لا يوجد رمز محفوظ؛ لا نتوقف
                                            hintText.text = "لا يوجد رمز محفوظ. احفظ QR من الشاشة الرئيسية"
                                        }
                                    }
                                }
                                .addOnFailureListener { }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, analyzer)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopAlarmAndFinish() {
        try {
            val stopIntent = Intent(this, AlarmService::class.java).apply { action = AlarmService.ACTION_STOP }
            ContextCompat.startForegroundService(this, stopIntent)
        } catch (_: Exception) {}
        Toast.makeText(this, "تم إيقاف المنبّه عبر QR", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
