package com.example.cyberapp

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.modules.session_inspector.SessionInspectorActivity
import com.example.cyberapp.modules.url_inspector.UrlScanActivity
import com.example.cyberapp.network.DomainBlocklist
import com.example.cyberapp.utils.AnimUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), AnomalyAdapter.OnAnomalyInteractionListener {
    private val tag = "MainActivity"
    private val logFileName = "behaviour_logs.jsonl"
    private lateinit var anomaliesRecyclerView: RecyclerView
    private lateinit var protectionButton: Button
    private lateinit var anomalyAdapter: AnomalyAdapter
    private val anomalyList = mutableListOf<Anomaly>()
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var lockOverlay: FrameLayout

    private val prefs: SharedPreferences by lazy { getSharedPreferences("CyberAppPrefs", MODE_PRIVATE) }
    private val pinManager: PinManager by lazy { PinManager(this) }
    private val securityManager: SecurityManager by lazy { SecurityManager(this) }
    private val encryptedLogger: EncryptedLogger by lazy { EncryptedLogger(this) }
    
    private val pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lockOverlay.isVisible = false
            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
        } else {
            if (lockOverlay.isVisible) {
                finish()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.READ_PHONE_STATE] == true) {
            startProtection()
        } else {
            Toast.makeText(this, "Qo'ng'iroq xavfsizligi uchun ruxsat kerak", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        protectionButton = findViewById(R.id.btn_protection)
        lockOverlay = findViewById(R.id.lock_overlay)
        
        biometricManager = BiometricAuthManager(this)
        
        val onboardingManager = OnboardingManager(this)
        if (onboardingManager.isFirstLaunch()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setupRecyclerView()
        setupButtonsAndListeners()
        updateAnomaliesView()
        
        lifecycleScope.launch {
            performSecurityChecks()
            checkRootStatus()
        }
        
        authenticateUser()
    }

    override fun onResume() {
        super.onResume()
        updateProtectionButton()
    }
    
    private fun performSecurityChecks() {
        val securityCheck = securityManager.performSecurityCheck()
        if (securityCheck.isDebuggerAttached || securityCheck.isApkTampered) {
            runOnUiThread {
                showSecurityThreatDialog(securityCheck)
            }
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
            lockOverlay.isVisible = false
            return
        }

        if (biometricManager.canAuthenticate()) {
            biometricManager.authenticate(
                onSuccess = { lockOverlay.isVisible = false },
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

    private fun setupButtonsAndListeners() {
        val actionUrlScan = findViewById<CardView>(R.id.action_url_scan)
        val actionSession = findViewById<CardView>(R.id.action_session)
        val actionApps = findViewById<CardView>(R.id.action_apps)
        val actionPermissions = findViewById<CardView>(R.id.action_permissions)
        val settingsButton = findViewById<ImageView>(R.id.settings_icon)

        AnimUtils.applyScaleAnimation(actionUrlScan)
        AnimUtils.applyScaleAnimation(actionSession)
        AnimUtils.applyScaleAnimation(actionApps)
        AnimUtils.applyScaleAnimation(actionPermissions)
        AnimUtils.applyScaleAnimation(settingsButton)

        actionUrlScan.setOnClickListener { 
            vibrateDevice()
            startActivity(Intent(this, UrlScanActivity::class.java))
        }
        actionSession.setOnClickListener { 
            vibrateDevice()
            startActivity(Intent(this, SessionInspectorActivity::class.java))
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
        findViewById<AppCompatButton>(R.id.btn_unlock).setOnClickListener { 
            vibrateDevice()
            authenticateUser()
        }
        
        protectionButton.setOnClickListener { 
            vibrateDevice()
            toggleProtection()
        }
    }

    private fun toggleProtection() {
        if (LoggerService.isRunning || CyberVpnService.isRunning) {
            stopProtection()
        } else {
            startProtection()
        }
    }

    private fun startProtection() {
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            Toast.makeText(this, "Iltimos, avval ruxsat bering", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
             requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE))
             return
        }
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnLauncher.launch(vpnIntent)
        } else {
            startService(Intent(this, CyberVpnService::class.java).setAction(CyberVpnService.ACTION_CONNECT))
        }
        val serviceIntent = Intent(this, LoggerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Himoya faollashtirildi", Toast.LENGTH_SHORT).show()
        updateProtectionButton()
    }

    private fun stopProtection() {
        startService(Intent(this, CyberVpnService::class.java).setAction(CyberVpnService.ACTION_DISCONNECT))
        stopService(Intent(this, LoggerService::class.java))
        Toast.makeText(this, "Himoya to'xtatildi", Toast.LENGTH_SHORT).show()
        updateProtectionButton()
    }

    private fun updateProtectionButton() {
        val isRunning = LoggerService.isRunning || CyberVpnService.isRunning
        if (isRunning) {
            protectionButton.text = getString(R.string.btn_stop_guard)
            protectionButton.setBackgroundResource(R.drawable.bg_main_action_button_active)
        } else {
            protectionButton.text = getString(R.string.btn_start_guard)
            protectionButton.setBackgroundResource(R.drawable.bg_main_action_button)
        }
    }
    
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startProtection()
        }
    }

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
            prefs.edit { putBoolean(exceptionKey, true) }
            anomalyList.removeAt(position)
            anomalyAdapter.notifyItemRemoved(position)
            Toast.makeText(this, "Istisno saqlandi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { 
            Log.e(tag, "Failed to mark as normal: ${e.message}") 
        } 
    }

    override fun onBlockIp(ip: String) {
        DomainBlocklist.add(ip)
        Toast.makeText(this, "$ip bloklandi", Toast.LENGTH_SHORT).show()
        updateAnomaliesView() // Refresh the list
    }

    override fun onUninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
        startActivity(intent)
    }

    private fun updateAnomaliesView() { 
        lifecycleScope.launch { 
            val newAnomalies = withContext(Dispatchers.IO) {
                val anomalies = mutableListOf<Anomaly>()
                val logContent = encryptedLogger.readLog(logFileName)
                
                if (logContent.isNotEmpty()) { 
                    val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                    try { 
                        logContent.lineSequence().toList().reversed().take(20).forEach { line ->
                            try { 
                                if (line.isNotEmpty()) {
                                    val json = JSONObject(line)
                                    val type = json.optString("type")
                                    if (type == "ANOMALY" || type == "ANOMALY_NETWORK") { 
                                        val description = json.optString("description")
                                        val exceptionKey = "exception_" + description.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")
                                        if (!prefs.getBoolean(exceptionKey, false)) {
                                             val timestamp = json.optLong("timestamp")
                                            anomalies.add(Anomaly(dateFormat.format(Date(timestamp)), description, line))
                                        }
                                    } 
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing log line", e)
                            } 
                        } 
                    } catch (e: Exception) {
                        Log.e(tag, "Error reading log file", e)
                    } 
                }
                anomalies
            }
            
            anomalyList.clear()
            if (newAnomalies.isNotEmpty()) { 
                anomalyList.addAll(newAnomalies)
            }
            anomalyAdapter.notifyDataSetChanged()
        } 
    }

    private suspend fun checkRootStatus() = withContext(Dispatchers.IO) {
        val rootDetector = RootDetector(this@MainActivity)
        if (rootDetector.isRooted()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "ROOT ANIQLANDI! Xavfsizlik buzilgan.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}
