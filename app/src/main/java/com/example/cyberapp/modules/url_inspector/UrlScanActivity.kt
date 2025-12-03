package com.example.cyberapp.modules.url_inspector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cyberapp.R
import com.example.cyberapp.network.RetrofitClient
import com.example.cyberapp.network.UrlCheckRequest
import kotlinx.coroutines.launch

class UrlScanActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvUrl: TextView
    private lateinit var tvVerdict: TextView
    private lateinit var tvReasons: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnOpenAnyway: Button
    private lateinit var btnGoBack: Button
    private lateinit var layoutSafe: View
    private lateinit var layoutDanger: View
    private lateinit var layoutScanning: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_scan)

        // Initialize Views (We will update XML next)
        tvTitle = findViewById(R.id.tvTitle)
        tvUrl = findViewById(R.id.tvUrl)
        tvVerdict = findViewById(R.id.tvVerdict)
        tvReasons = findViewById(R.id.tvReasons)
        progressBar = findViewById(R.id.progressBar)
        btnOpenAnyway = findViewById(R.id.btnOpenAnyway)
        btnGoBack = findViewById(R.id.btnGoBack)
        
        // New Layouts for states (will add to XML)
        // For now, we assume simple visibility toggling of existing elements or new ones if we add them.
        // Let's stick to existing IDs for now and just manage visibility logic carefully.

        val url = intent.dataString

        if (url != null) {
            tvUrl.text = url
            startScanning(url)
        } else {
            finish() // Nothing to scan
        }

        btnGoBack.setOnClickListener {
            finish()
        }
        
        btnOpenAnyway.setOnClickListener {
             // Dangerous open
             openUrlExternal(tvUrl.text.toString())
        }
    }

    private fun startScanning(url: String) {
        // Show Scanning UI
        tvTitle.text = "TEKSHIRILMOQDA..."
        tvTitle.setTextColor(getColor(R.color.neon_cyan))
        progressBar.visibility = View.VISIBLE
        tvVerdict.visibility = View.GONE
        tvReasons.visibility = View.GONE
        btnOpenAnyway.visibility = View.GONE
        btnGoBack.visibility = View.GONE
        
        com.example.cyberapp.utils.NotificationHelper.showScanningNotification(this, url)

        lifecycleScope.launch {
            // Simulate min delay for UX (so user sees "Scanning")
            kotlinx.coroutines.delay(800) 
            
            try {
                val response = RetrofitClient.api.checkUrl(UrlCheckRequest(url))
                handleScanResult(url, response)
            } catch (e: Exception) {
                // Fallback to local check if network fails
                val localResult = PhishingDetector.analyzeUrl(url)
                if (localResult.isSuspicious) {
                     handleLocalDanger(url, localResult)
                } else {
                     // Assume safe if local check passes and network failed (or show error)
                     // For seamlessness, let's assume safe but warn about offline
                     handleSafeResult(url)
                }
            }
        }
    }

    private fun handleScanResult(url: String, response: com.example.cyberapp.network.UrlCheckResponse) {
        // Save to History
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dao = com.example.cyberapp.database.AppDatabase.getDatabase(this@UrlScanActivity).scanHistoryDao()
            dao.insert(com.example.cyberapp.database.ScanHistoryEntity(
                url = url,
                verdict = response.verdict,
                timestamp = System.currentTimeMillis()
            ))
        }

        if (response.verdict == "safe") {
            handleSafeResult(url)
        } else {
            handleDangerResult(url, response.reasons)
        }
    }

    private fun handleLocalDanger(url: String, result: PhishingDetector.UrlAnalysisResult) {
        // Save to History
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dao = com.example.cyberapp.database.AppDatabase.getDatabase(this@UrlScanActivity).scanHistoryDao()
            dao.insert(com.example.cyberapp.database.ScanHistoryEntity(
                url = url,
                verdict = "dangerous",
                timestamp = System.currentTimeMillis()
            ))
        }
        handleDangerResult(url, result.warnings)
    }

    private fun handleSafeResult(url: String) {
        // UI Update
        progressBar.visibility = View.GONE
        tvTitle.text = "XAVFSIZ"
        tvTitle.setTextColor(getColor(R.color.neon_green))
        tvVerdict.text = "✅ Havola xavfsiz"
        tvVerdict.visibility = View.VISIBLE
        
        com.example.cyberapp.utils.NotificationHelper.showSafeNotification(this, url)

        // Check Auto-Open Preference
        val prefs = getSharedPreferences("CyberAppPrefs", Context.MODE_PRIVATE)
        val autoOpen = prefs.getBoolean("autoOpenSafeUrls", true)

        if (autoOpen) {
            // Auto Redirect after 1s
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                openUrlExternal(url)
            }
        } else {
            // Show Open Button
            btnOpenAnyway.visibility = View.VISIBLE
            btnOpenAnyway.text = "HAVOLANI OCHISH"
            btnOpenAnyway.setTextColor(getColor(R.color.neon_green))
            btnOpenAnyway.setOnClickListener {
                openUrlExternal(url)
            }
        }
    }

    private fun handleDangerResult(url: String, reasons: List<String>) {
        // UI Update
        progressBar.visibility = View.GONE
        tvTitle.text = "XAVFLI!"
        tvTitle.setTextColor(getColor(R.color.neon_red))
        tvVerdict.text = "🚨 KIRISH BLOKLANDI"
        tvVerdict.visibility = View.VISIBLE
        
        tvReasons.text = reasons.joinToString("\n") { "• $it" }
        tvReasons.visibility = View.VISIBLE
        
        btnGoBack.visibility = View.VISIBLE
        btnOpenAnyway.visibility = View.VISIBLE
        btnOpenAnyway.text = "Xavfga rozi bo'lib kirish"
        btnOpenAnyway.setTextColor(getColor(R.color.neon_red))

        com.example.cyberapp.utils.NotificationHelper.showDangerNotification(this, url)
        vibrateDevice()
    }

    private fun openUrlExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // Exclude our own app from the chooser to avoid loops
            // Actually, if we are the default, we might loop. 
            // We need to set package to Chrome or show chooser excluding us.
            // Simple way: just start activity. If we are default, we need logic to not catch it again.
            // But usually "browser" intent filters are for http/https.
            // If we are the default browser, we catch everything.
            // To open in *another* browser, we might need to find one.
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            // No browser found? Open in internal SafeWebView
            val intent = Intent(this, com.example.cyberapp.modules.safe_webview.SafeWebViewActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
            finish()
        }
    }

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }
}
