package com.example.cyberapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.modules.url_inspector.UrlScanActivity
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), AnomalyAdapter.OnAnomalyInteractionListener {
    private val TAG = "MainActivity"
    private val LOG_FILE_NAME = "behaviour_logs.jsonl"
    private lateinit var anomaliesRecyclerView: RecyclerView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var anomalyAdapter: AnomalyAdapter
    private val anomalyList = mutableListOf<Anomaly>()
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var lockOverlay: android.widget.FrameLayout
    private lateinit var encryptedLogger: EncryptedLogger
    
    //<editor-fold desc="Lifecycle and Launchers">
    private lateinit var pinManager: PinManager
    private lateinit var securityManager: SecurityManager
    
    private val pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lockOverlay.visibility = android.view.View.GONE
            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
        } else {
            if (lockOverlay.visibility == android.view.View.VISIBLE) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusTitle = findViewById(R.id.status_title)
        statusSubtitle = findViewById(R.id.status_subtitle)
        lockOverlay = findViewById(R.id.lock_overlay)
        
        biometricManager = BiometricAuthManager(this)
        prefs = EncryptedPrefsManager(this)
        pinManager = PinManager(this)
        securityManager = SecurityManager(this)
        encryptedLogger = EncryptedLogger(this)
        
        // Onboarding Check
        val onboardingManager = OnboardingManager(this)
        if (onboardingManager.isFirstLaunch()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setupRecyclerView()
        setupButtonsAndListeners()
        updateStatusView()
        updateAnomaliesView()
        
        // Security checks
        performSecurityChecks()
        checkRootStatus()
        authenticateUser()
    }
    
    private fun performSecurityChecks() {
        val securityCheck = securityManager.performSecurityCheck()
        if (securityCheck.isDebuggerAttached || securityCheck.isApkTampered) {
            showSecurityThreatDialog(securityCheck)
        }
    }
    
    private fun showSecurityThreatDialog(securityCheck: SecurityCheckResult) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Security Alert")
            .setMessage("Critical threat detected:\n\n${securityCheck.getThreatDescription()}\n\nApp will close.")
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ -> finish() }
            .show()
    }
    
    private fun authenticateUser() {
        if (!pinManager.isPinSet()) {
            lockOverlay.visibility = android.view.View.GONE
            return
        }

        if (biometricManager.canAuthenticate()) {
            biometricManager.authenticate(
                onSuccess = { lockOverlay.visibility = android.view.View.GONE },
                onError = { launchPinFallback() },
                onFailed = { launchPinFallback() }
            )
        } else {
            launchPinFallback()
        }
    }

    private fun launchPinFallback() {
        val intent = Intent(this, PinActivity::class.java)
        pinLauncher.launch(intent)
    }
    //</editor-fold>

    //<editor-fold desc="Setup and Listeners">
    private fun setupButtonsAndListeners() {
        val btnQuickScan = findViewById<Button>(R.id.btn_quick_scan)
        val actionUrlScan = findViewById<androidx.cardview.widget.CardView>(R.id.action_url_scan)
        val actionSession = findViewById<androidx.cardview.widget.CardView>(R.id.action_session)
        val actionApps = findViewById<androidx.cardview.widget.CardView>(R.id.action_apps)
        val actionPermissions = findViewById<androidx.cardview.widget.CardView>(R.id.action_permissions)
        val settingsButton = findViewById<android.widget.ImageView>(R.id.settings_icon) // Changed from settings_button to settings_icon

        // Apply Animations
        com.example.cyberapp.utils.AnimUtils.applyScaleAnimation(btnQuickScan)
        com.example.cyberapp.utils.AnimUtils.applyScaleAnimation(actionUrlScan)
        com.example.cyberapp.utils.AnimUtils.applyScaleAnimation(actionSession)
        com.example.cyberapp.utils.AnimUtils.applyScaleAnimation(actionApps)
        com.example.cyberapp.utils.AnimUtils.applyScaleAnimation(actionPermissions)
        com.example.cyberapp.utils.AnimUtils.applyScaleAnimation(settingsButton)

        btnQuickScan.setOnClickListener {
            vibrateDevice()
            // Quick Scan Logic (Simulated)
            Toast.makeText(this, "Quick System Scan Started...", Toast.LENGTH_SHORT).show()
        }

        actionUrlScan.setOnClickListener {
            vibrateDevice()
            startActivity(Intent(this, com.example.cyberapp.modules.url_inspector.UrlScanActivity::class.java))
        }
        
        actionSession.setOnClickListener {
            vibrateDevice()
            startActivity(Intent(this, com.example.cyberapp.modules.session_inspector.SessionInspectorActivity::class.java))
        }
        
        actionApps.setOnClickListener {
            vibrateDevice()
            startActivity(Intent(this, AppAnalysisActivity::class.java))
        }
        
        actionPermissions.setOnClickListener {
            vibrateDevice()
            val intent = Intent(this, AppAnalysisActivity::class.java)
            intent.putExtra("TITLE", "Permission Manager")
            startActivity(intent)
        }

        settingsButton.setOnClickListener { 
            vibrateDevice()
            startActivity(Intent(this, SettingsActivity::class.java)) 
        }

        // Unlock Button
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_unlock).setOnClickListener {
            vibrateDevice()
            authenticateUser()
        }
    }
    //</editor-fold>

    //<editor-fold desc="UI Updates">
    private fun setupRecyclerView() { 
        anomaliesRecyclerView = findViewById(R.id.anomalies_recyclerview)
        anomalyAdapter = AnomalyAdapter(anomalyList, this)
        anomaliesRecyclerView.adapter = anomalyAdapter
        anomaliesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onMarkAsNormal(anomaly: Anomaly, position: Int) { 
        try { 
            val json = JSONObject(anomaly.rawJson)
            val description = json.getString("description")
            val exceptionKey = "exception_" + description.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")
            prefs.edit().putBoolean(exceptionKey, true).apply()
            anomalyList.removeAt(position)
            anomalyAdapter.notifyItemRemoved(position)
            Toast.makeText(this, "Exception saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { 
            Log.e(TAG, "Failed to mark as normal: ${e.message}") 
        } 
    }

    private fun updateStatusView() { 
        // Simple status update for now
        statusTitle.text = "System Secure"
        statusSubtitle.text = "Real-time protection is active."
    }

    private fun updateAnomaliesView() { 
        thread { 
            val newAnomalies = mutableListOf<Anomaly>()
            val logContent = encryptedLogger.readLog(LOG_FILE_NAME)
            
            if (logContent.isNotEmpty()) { 
                val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                try { 
                    logContent.lineSequence().take(10).forEach { line -> // Limit to last 10
                        try { 
                            if (line.isNotEmpty()) {
                                val json = JSONObject(line)
                                val type = json.getString("type")
                                if (type == "ANOMALY" || type == "ANOMALY_NETWORK") { 
                                    val description = json.getString("description")
                                    val timestamp = json.getLong("timestamp")
                                    newAnomalies.add(Anomaly(dateFormat.format(Date(timestamp)), description, line))
                                } 
                            }
                        } catch (e: Exception) { } 
                    } 
                } catch (e: Exception) { } 
            }
            
            runOnUiThread { 
                anomalyList.clear()
                if (newAnomalies.isNotEmpty()) { 
                    anomalyList.addAll(newAnomalies)
                }
                anomalyAdapter.notifyDataSetChanged()
            } 
        } 
    }
    //</editor-fold>

    //<editor-fold desc="Root Detection">
    private fun checkRootStatus() {
        val rootDetector = RootDetector(this)
        if (rootDetector.isRooted()) {
            Toast.makeText(this, "ROOT DETECTED! Security compromised.", Toast.LENGTH_LONG).show()
        }
    }
    //</editor-fold>

    //<editor-fold desc="Haptic Feedback">
    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
    //</editor-fold>
}
