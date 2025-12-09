package com.example.cyberapp.modules.url_inspector

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.cyberapp.R
import com.example.cyberapp.EncryptedPrefsManager
import com.example.cyberapp.network.RetrofitClient
import com.example.cyberapp.network.UrlCheckRequest
import com.example.cyberapp.network.CheckResponse
import com.example.cyberapp.network.AuthManager
import com.example.cyberapp.PhishingDetector
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UrlScanActivity : AppCompatActivity() {

    companion object {
        private const val SCAN_STEP_DELAY_MS = 400L
        private const val URL_TEST_DELAY_MS = 10000L
        private const val RISK_SCORE_THRESHOLD = 30
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvUrl: TextView
    private lateinit var lottieScan: LottieAnimationView
    private lateinit var tvStatus: TextView
    private lateinit var layoutVerdict: CardView
    private lateinit var iconVerdict: ImageView
    private lateinit var tvVerdictTitle: TextView
    private lateinit var tvVerdictDesc: TextView
    private lateinit var btnMainAction: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnSecondaryAction: androidx.appcompat.widget.AppCompatButton
    
    private lateinit var urlInputLayout: TextInputLayout
    private lateinit var urlInput: TextInputEditText
    private lateinit var btnScan: androidx.appcompat.widget.AppCompatButton
    private var isFromExternalIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_scan)

        // Set activity to be dismissable by touching outside
        setFinishOnTouchOutside(true)

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
        urlInputLayout = findViewById(R.id.url_input_layout)
        urlInput = findViewById(R.id.url_input)
        btnScan = findViewById(R.id.btn_scan)

        val url = intent.dataString
        val runTest = intent.getBooleanExtra("run_test", false)

        if (runTest) {
            lifecycleScope.launch { runUrlScanTest() }
            return
        }

        if (url != null) {
            isFromExternalIntent = true
            urlInputLayout.visibility = View.GONE
            btnScan.visibility = View.GONE
            tvUrl.visibility = View.VISIBLE
            tvUrl.text = url
            startScanning(url)
        } else {
            isFromExternalIntent = false
            setupManualMode()
        }
    }

    private suspend fun runUrlScanTest() {
        val testUrls = listOf(
            "http://example-login-page.com/",
            "http://secure-bank-update.net/auth",
            "https://google.com" 
        )

        for (testUrl in testUrls) {
            Log.d("UrlScanTest", "Testing URL: $testUrl")
            runOnUiThread {
                urlInputLayout.visibility = View.GONE
                btnScan.visibility = View.GONE
                tvUrl.visibility = View.VISIBLE
                tvUrl.text = testUrl
                startScanning(testUrl)
            }
            delay(URL_TEST_DELAY_MS) 
        }
        Log.d("UrlScanTest", "Test finished.")
        finish() 
    }

    private fun setupManualMode() {
        tvTitle.text = getString(R.string.manual_url_scan)
        urlInputLayout.visibility = View.VISIBLE
        btnScan.visibility = View.VISIBLE
        tvUrl.visibility = View.GONE
        lottieScan.visibility = View.GONE
        tvStatus.visibility = View.GONE
        layoutVerdict.visibility = View.GONE
        btnMainAction.visibility = View.GONE
        btnSecondaryAction.visibility = View.GONE
        
        btnScan.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (validateUrl(url)) {
                hideKeyboard()
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
            urlInputLayout.error = getString(R.string.url_empty_error)
            return false
        }
        
        return try {
            val uri = android.net.Uri.parse(url)
            if (uri.scheme == null || uri.host == null) {
                urlInputLayout.error = getString(R.string.url_invalid_error)
                false
            } else {
                // Additional validation: check for safe schemes only
                val scheme = uri.scheme?.lowercase()
                if (scheme !in listOf("http", "https")) {
                    urlInputLayout.error = getString(R.string.url_invalid_error)
                    false
                } else {
                    urlInputLayout.error = null
                    true
                }
            }
        } catch (e: Exception) {
            urlInputLayout.error = getString(R.string.url_invalid_error)
            false
        }
    }
    
    private fun resetToInputMode() {
        setupManualMode()
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    private fun startScanning(url: String) {
        tvTitle.text = getString(R.string.scanning_url)
        tvTitle.setTextColor(getColor(R.color.text_primary))
        lottieScan.visibility = View.VISIBLE
        lottieScan.playAnimation()
        tvStatus.visibility = View.VISIBLE
        layoutVerdict.visibility = View.GONE
        btnMainAction.visibility = View.GONE
        btnSecondaryAction.visibility = View.GONE
        
        lifecycleScope.launch {
            val steps = listOf(
                getString(R.string.scan_step_initializing),
                getString(R.string.scan_step_analyzing),
                getString(R.string.scan_step_finalizing)
            )
            
            for (step in steps) {
                tvStatus.text = step
                delay(SCAN_STEP_DELAY_MS) 
            }
            
            performCheck(url)
        }
    }
    
    private suspend fun performCheck(url: String) {
        try {
            val prefs = EncryptedPrefsManager(this)
            val token = AuthManager.getAuthToken(prefs)
            val authHeader = if (token != null) "Bearer $token" else null
            
            val response = RetrofitClient.api.checkUrl(UrlCheckRequest(url), authHeader)
            handleScanResult(url, response)
        } catch (e: java.net.UnknownHostException) {
            // Network error - host not found
            withContext(Dispatchers.Main) {
                showErrorDialog(getString(R.string.network_error_host_not_found))
            }
        } catch (e: java.net.SocketTimeoutException) {
            // Timeout error
            withContext(Dispatchers.Main) {
                showErrorDialog(getString(R.string.network_error_timeout))
            }
        } catch (e: java.io.IOException) {
            // General network error
            withContext(Dispatchers.Main) {
                showErrorDialog(getString(R.string.network_error_general))
            }
            // Fallback to local analysis
            val localResult = PhishingDetector.analyzeUrl(url)
            if (localResult.isSuspicious || localResult.riskScore >= RISK_SCORE_THRESHOLD) {
                handleDangerResult(url, localResult.warnings)
            } else {
                handleSafeResult(url)
            }
        } catch (e: Exception) {
            // Unknown error
            withContext(Dispatchers.Main) {
                showErrorDialog(getString(R.string.unknown_error, e.message ?: "Unknown"))
            }
            // Fallback to local analysis
            val localResult = PhishingDetector.analyzeUrl(url)
            if (localResult.isSuspicious || localResult.riskScore >= RISK_SCORE_THRESHOLD) {
                handleDangerResult(url, localResult.warnings)
            } else {
                handleSafeResult(url)
            }
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                finish()
            }
            .setCancelable(true)
            .show()
    }

    private fun handleScanResult(url: String, response: CheckResponse) {
        lifecycleScope.launch(Dispatchers.IO) {
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
        tvVerdictTitle.text = getString(R.string.url_safe_title)
        tvVerdictTitle.setTextColor(getColor(R.color.safe_green))
        tvVerdictDesc.text = getString(R.string.url_safe_message)
        
        btnMainAction.visibility = View.VISIBLE
        btnMainAction.text = getString(R.string.open_in_browser)
        btnMainAction.setTextColor(getColor(R.color.white))
        btnMainAction.setOnClickListener { 
            openUrlExternal(url)
            finish()
        }
        
        btnSecondaryAction.visibility = View.VISIBLE
        btnSecondaryAction.text = getString(R.string.cancel)
        btnSecondaryAction.setOnClickListener { finish() }
        
        vibrateDevice(success = true)
        
        // Auto-open if setting is enabled
        val prefs = EncryptedPrefsManager(this)
        if (prefs.getBoolean("autoOpenSafeUrls", true)) {
            // Small delay to show result, then auto-open
            lifecycleScope.launch {
                delay(1500)
                openUrlExternal(url)
                finish()
            }
        }
    }

    private fun handleDangerResult(url: String, reasons: List<String>) {
        lottieScan.cancelAnimation()
        lottieScan.visibility = View.GONE
        tvStatus.visibility = View.GONE
        
        layoutVerdict.visibility = View.VISIBLE
        iconVerdict.setImageResource(R.drawable.ic_lock)
        iconVerdict.setColorFilter(getColor(R.color.neon_red))
        tvVerdictTitle.text = getString(R.string.threat_detected_title)
        tvVerdictTitle.setTextColor(getColor(R.color.neon_red))
        
        val reasonText = if (reasons.isNotEmpty()) reasons.joinToString("\n") else getString(R.string.suspicious_activity_detected)
        tvVerdictDesc.text = reasonText
        
        btnMainAction.visibility = View.VISIBLE
        btnMainAction.text = getString(R.string.close)
        btnMainAction.setTextColor(getColor(R.color.white))
        btnMainAction.setOnClickListener { finish() }
        
        btnSecondaryAction.visibility = View.GONE
        
        vibrateDevice(success = false)
    }

    private fun openUrlExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.could_not_open_url), Toast.LENGTH_SHORT).show()
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
