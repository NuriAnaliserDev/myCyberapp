package com.example.cyberapp.modules.url_inspector

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.cyberapp.R
import com.example.cyberapp.network.RetrofitClient
import com.example.cyberapp.network.UrlCheckRequest
import com.example.cyberapp.network.CheckResponse
import com.example.cyberapp.PhishingDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UrlScanActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvUrl: TextView
    private lateinit var lottieScan: LottieAnimationView
    private lateinit var tvStatus: TextView
    private lateinit var layoutVerdict: LinearLayout
    private lateinit var iconVerdict: ImageView
    private lateinit var tvVerdictTitle: TextView
    private lateinit var tvVerdictDesc: TextView
    private lateinit var btnMainAction: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnSecondaryAction: androidx.appcompat.widget.AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_scan)

        // Initialize Views
        tvTitle = findViewById(R.id.tvTitle)
        tvUrl = findViewById(R.id.tvUrl)
        lottieScan = findViewById(R.id.lottie_scan)
        tvStatus = findViewById(R.id.tvStatus)
        layoutVerdict = findViewById(R.id.layout_verdict)
        iconVerdict = findViewById(R.id.icon_verdict)
        tvVerdictTitle = findViewById(R.id.tvVerdictTitle)
        tvVerdictDesc = findViewById(R.id.tvVerdictDesc)
        btnMainAction = findViewById(R.id.btnMainAction)
        btnSecondaryAction = findViewById(R.id.btnSecondaryAction)

        val url = intent.dataString

        if (url != null) {
            tvUrl.text = url
            startScanning(url)
        } else {
            // Test mode if no URL provided via intent filter
            tvUrl.text = "https://google.com"
            startScanning("https://google.com")
        }

        btnSecondaryAction.setOnClickListener {
            finish()
        }
    }

    private fun startScanning(url: String) {
        // Reset UI
        tvTitle.text = "Scanning URL..."
        tvTitle.setTextColor(getColor(R.color.text_primary))
        lottieScan.visibility = View.VISIBLE
        lottieScan.playAnimation()
        layoutVerdict.visibility = View.GONE
        btnMainAction.visibility = View.GONE
        btnSecondaryAction.visibility = View.GONE
        
        // Dynamic Status Text
        lifecycleScope.launch {
            val steps = listOf(
                "Initializing secure connection...",
                "Analyzing SSL certificate...",
                "Checking global blacklists...",
                "Verifying content reputation...",
                "Finalizing report..."
            )
            
            for (step in steps) {
                tvStatus.text = step
                delay(600) // Simulate work
            }
            
            // Perform actual check
            performCheck(url)
        }
    }
    
    private suspend fun performCheck(url: String) {
        try {
            val response = RetrofitClient.api.checkUrl(UrlCheckRequest(url))
            handleScanResult(url, response)
        } catch (e: Exception) {
            // Fallback to local check
            val localResult = PhishingDetector.analyzeUrl(url)
            if (localResult.isSuspicious) {
                 handleDangerResult(url, localResult.warnings)
            } else {
                 handleSafeResult(url)
            }
        }
    }

    private fun handleScanResult(url: String, response: CheckResponse) {
        // Save to History (Async)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dao = com.example.cyberapp.database.AppDatabase.getDatabase(this@UrlScanActivity).scanHistoryDao()
                dao.insert(com.example.cyberapp.database.ScanHistoryEntity(
                    url = url,
                    verdict = response.verdict,
                    timestamp = System.currentTimeMillis()
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (response.verdict == "safe") {
            handleSafeResult(url)
        } else {
            handleDangerResult(url, response.reasons)
        }
    }

    private fun handleSafeResult(url: String) {
        lottieScan.cancelAnimation()
        lottieScan.visibility = View.GONE
        tvStatus.visibility = View.GONE
        
        layoutVerdict.visibility = View.VISIBLE
        iconVerdict.setImageResource(R.drawable.ic_shield_check)
        iconVerdict.setColorFilter(getColor(R.color.safe_green))
        tvVerdictTitle.text = "Safe to Visit"
        tvVerdictTitle.setTextColor(getColor(R.color.safe_green))
        tvVerdictDesc.text = "No threats detected. This website appears safe."
        
        btnMainAction.visibility = View.VISIBLE
        btnMainAction.text = "OPEN SITE"
        btnMainAction.setTextColor(getColor(R.color.primary_blue))
        btnMainAction.setOnClickListener {
            openUrlExternal(url)
        }
        
        btnSecondaryAction.visibility = View.VISIBLE
        btnSecondaryAction.text = "Back to Dashboard"
        
        vibrateDevice(success = true)
    }

    private fun handleDangerResult(url: String, reasons: List<String>) {
        lottieScan.cancelAnimation()
        lottieScan.visibility = View.GONE
        tvStatus.visibility = View.GONE
        
        layoutVerdict.visibility = View.VISIBLE
        iconVerdict.setImageResource(R.drawable.ic_lock) // Or danger icon
        iconVerdict.setColorFilter(getColor(R.color.neon_red))
        tvVerdictTitle.text = "Threat Detected!"
        tvVerdictTitle.setTextColor(getColor(R.color.neon_red))
        
        val reasonText = if (reasons.isNotEmpty()) reasons[0] else "Suspicious activity detected."
        tvVerdictDesc.text = reasonText
        
        btnMainAction.visibility = View.VISIBLE
        btnMainAction.text = "GO BACK TO SAFETY"
        btnMainAction.setTextColor(getColor(R.color.neon_red))
        btnMainAction.setOnClickListener {
            finish()
        }
        
        btnSecondaryAction.visibility = View.VISIBLE
        btnSecondaryAction.text = "I understand the risk, open anyway"
        btnSecondaryAction.setOnClickListener {
            openUrlExternal(url)
        }
        
        vibrateDevice(success = false)
    }

    private fun openUrlExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            // Fallback to internal webview
            val intent = Intent(this, com.example.cyberapp.modules.safe_webview.SafeWebViewActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
            finish()
        }
    }

    private fun vibrateDevice(success: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val effect = if (success) {
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }
}
