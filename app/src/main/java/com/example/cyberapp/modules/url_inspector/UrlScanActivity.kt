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
import android.view.inputmethod.InputMethodManager
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    
    // New: URL Input fields
    private lateinit var urlInputLayout: TextInputLayout
    private lateinit var urlInput: TextInputEditText
    private lateinit var btnScan: androidx.appcompat.widget.AppCompatButton
    private var isFromExternalIntent = false

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
        
        // New: Input fields
        urlInputLayout = findViewById(R.id.url_input_layout)
        urlInput = findViewById(R.id.url_input)
        btnScan = findViewById(R.id.btn_scan)

        // Check if URL came from external intent (e.g., Chrome, Telegram)
        val url = intent.dataString

        if (url != null) {
            // External intent mode - hide input, auto scan
            isFromExternalIntent = true
            urlInputLayout.visibility = View.GONE
            btnScan.visibility = View.GONE
            tvUrl.visibility = View.VISIBLE
            tvUrl.text = url
            startScanning(url)
        } else {
            // Manual mode - show input field
            isFromExternalIntent = false
            setupManualMode()
        }
    }

    private fun setupManualMode() {
        // Show input UI, hide scanning/result UI
        urlInputLayout.visibility = View.VISIBLE
        btnScan.visibility = View.VISIBLE
        tvUrl.visibility = View.GONE
        lottieScan.visibility = View.GONE
        tvStatus.visibility = View.GONE
        layoutVerdict.visibility = View.GONE
        btnMainAction.visibility = View.GONE
        btnSecondaryAction.visibility = View.GONE
        
        // Scan button click handler
        btnScan.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (validateUrl(url)) {
                hideKeyboard()
                // Hide input, show URL display
                urlInputLayout.visibility = View.GONE
                btnScan.visibility = View.GONE
                tvUrl.visibility = View.VISIBLE
                tvUrl.text = url
                startScanning(url)
            }
        }
    }
    
    private fun validateUrl(url: String): Boolean {
        if (url.isEmpty()) {
            urlInputLayout.error = "URL cannot be empty"
            return false
        }
        
        return try {
            val uri = java.net.URI(url)
            if (uri.scheme == null || uri.host == null) {
                urlInputLayout.error = "Invalid URL format. Example: https://google.com"
                false
            } else {
                urlInputLayout.error = null
                true
            }
        } catch (e: Exception) {
            urlInputLayout.error = "Invalid URL format. Example: https://google.com"
            false
        }
    }
    
    private fun resetToInputMode() {
        // Show input UI
        urlInputLayout.visibility = View.VISIBLE
        btnScan.visibility = View.VISIBLE
        
        // Hide scanning/result UI
        tvUrl.visibility = View.GONE
        lottieScan.visibility = View.GONE
        tvStatus.visibility = View.GONE
        layoutVerdict.visibility = View.GONE
        btnMainAction.visibility = View.GONE
        btnSecondaryAction.visibility = View.GONE
        
        // Clear input
        urlInput.text?.clear()
        urlInputLayout.error = null
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
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
            if (localResult.isSuspicious || localResult.riskScore >= 30) {
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
        btnSecondaryAction.setOnClickListener {
            if (isFromExternalIntent) {
                finish()
            } else {
                resetToInputMode()
            }
        }
        
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
            if (isFromExternalIntent) {
                finish()
            } else {
                resetToInputMode()
            }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (success) {
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            if (success) {
                vibrator.vibrate(50)
            } else {
                vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
            }
        }
    }
}
