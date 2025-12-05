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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val protectionSwitch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_protection)
        if (permissions[android.Manifest.permission.READ_PHONE_STATE] == true) {
            // Retry enabling protection if permission granted
            if (protectionSwitch != null && !protectionSwitch.isChecked) {
                // Determine if we should automatically enable or let user click again.
                // For better UX, we just let them click again or we can manually trigger it.
                // Ideally, we should not block the checkbox but the service start logic.
                // But the checkbox listener is where we check.
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
        setupButtonsAndListeners()
        // updateStatusView() called inside setupButtonsAndListeners -> setupProtectionSwitch
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
            performQuickScan()
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
        
        setupVpnToggle()
        setupProtectionSwitch()
    }

    private fun setupProtectionSwitch() {
        val switchProtection = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_protection)
        
        // Initial State Check
        val isServiceRunning = isLoggerServiceRunning()
        switchProtection.isChecked = isServiceRunning
        updateStatusView(isServiceRunning)

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            vibrateDevice()
            if (isChecked) {
                if (!PermissionHelper.hasUsageStatsPermission(this)) {
                    Toast.makeText(this, "Iltimos, avval ruxsat bering", Toast.LENGTH_LONG).show()
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    switchProtection.isChecked = false
                    return@setOnCheckedChangeListener
                }

                if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                     Toast.makeText(this, "Qo'ng'iroq havfsizligi uchun ruxsat kerak", Toast.LENGTH_LONG).show()
                     requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE))
                     switchProtection.isChecked = false
                     return@setOnCheckedChangeListener
                }
                
                val serviceIntent = Intent(this, LoggerService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Himoya faollashtirildi", Toast.LENGTH_SHORT).show()
                updateStatusView(true)
            } else {
                val serviceIntent = Intent(this, LoggerService::class.java)
                stopService(serviceIntent)
                Toast.makeText(this, "Himoya to'xtatildi", Toast.LENGTH_SHORT).show()
                updateStatusView(false)
            }
        }
    }

    private fun isLoggerServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (LoggerService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun setupVpnToggle() {
        val actionVpn = findViewById<androidx.cardview.widget.CardView>(R.id.action_vpn)
        val iconVpn = findViewById<ImageView>(R.id.icon_vpn)
        val textVpnStatus = findViewById<TextView>(R.id.text_vpn_status)
        val iconVpnToggle = findViewById<ImageView>(R.id.icon_vpn_toggle)

        fun updateVpnUi() {
            val isVpnRunning = prefs.getBoolean("vpn_running", false)
            if (isVpnRunning) {
                iconVpn.setColorFilter(getColor(R.color.safe_green))
                textVpnStatus.text = "Active (Passive Mode)"
                textVpnStatus.setTextColor(getColor(R.color.safe_green))
                iconVpnToggle.setImageResource(R.drawable.ic_check)
                iconVpnToggle.setColorFilter(getColor(R.color.safe_green))
            } else {
                iconVpn.setColorFilter(getColor(R.color.text_secondary))
                textVpnStatus.text = "Tap to activate"
                textVpnStatus.setTextColor(getColor(R.color.text_secondary))
                iconVpnToggle.setImageResource(R.drawable.ic_arrow_right)
                iconVpnToggle.setColorFilter(getColor(R.color.text_secondary))
            }
        }

        // Initial State
        updateVpnUi()

        actionVpn.setOnClickListener {
            vibrateDevice()
            val intent = Intent(this, CyberVpnService::class.java)
            val isVpnRunning = prefs.getBoolean("vpn_running", false)
            
            if (isVpnRunning) {
                intent.action = CyberVpnService.ACTION_DISCONNECT
                startService(intent)
                CyberVpnService.isRunning = false
                prefs.edit().putBoolean("vpn_running", false).apply()
                updateVpnUi()
                Toast.makeText(this, "VPN Disconnected", Toast.LENGTH_SHORT).show()
            } else {
                // Prepare VPN (System Dialog)
                try {
                    val vpnIntent = android.net.VpnService.prepare(this)
                    if (vpnIntent != null) {
                        vpnLauncher.launch(vpnIntent)
                    } else {
                        intent.action = CyberVpnService.ACTION_CONNECT
                        startService(intent)
                        CyberVpnService.isRunning = true
                        prefs.edit().putBoolean("vpn_running", true).apply()
                        updateVpnUi()
                        Toast.makeText(this, "VPN Connected", Toast.LENGTH_SHORT).show()
                        showProtectionNotification()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "VPN start failed: ${e.message}")
                    Toast.makeText(this, "VPN xatolik: ${e.message}", Toast.LENGTH_LONG).show()
                    // If SecurityException, it might be a system bug or UID mismatch.
                    // We can try to reset the VPN state or just inform the user.
                    prefs.edit().putBoolean("vpn_running", false).apply()
                    updateVpnUi()
                }
            }
        }
    }
    
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, CyberVpnService::class.java)
            intent.action = CyberVpnService.ACTION_CONNECT
            startService(intent)
            CyberVpnService.isRunning = true
            prefs.edit().putBoolean("vpn_running", true).apply()
            
            // Update UI
            val iconVpn = findViewById<ImageView>(R.id.icon_vpn)
            val textVpnStatus = findViewById<TextView>(R.id.text_vpn_status)
            val iconVpnToggle = findViewById<ImageView>(R.id.icon_vpn_toggle)
            
            iconVpn.setColorFilter(getColor(R.color.safe_green))
            textVpnStatus.text = "Active (Passive Mode)"
            textVpnStatus.setTextColor(getColor(R.color.safe_green))
            iconVpnToggle.setImageResource(R.drawable.ic_check)
            iconVpnToggle.setColorFilter(getColor(R.color.safe_green))
            
            Toast.makeText(this, "VPN Connected", Toast.LENGTH_SHORT).show()
            showProtectionNotification()
        }
    }

    private fun performQuickScan() {
        statusTitle.text = "Scanning..."
        statusSubtitle.text = "Checking system security..."
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Root check
            val rootDetector = RootDetector(this@MainActivity)
            val isRooted = rootDetector.isRooted()
            
            // 2. Security check
            val securityCheck = securityManager.performSecurityCheck()
            
            // 3. Count suspicious apps
            val pm = packageManager
            val packages = pm.getInstalledPackages(android.content.pm.PackageManager.GET_PERMISSIONS)
            var suspiciousCount = 0
            for (pkg in packages) {
                val result = PhishingDetector.analyzePackage(this@MainActivity, pkg.packageName)
                if (result.isSuspicious) suspiciousCount++
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (isRooted || securityCheck.isDebuggerAttached || suspiciousCount > 0) {
                    statusTitle.text = "Threats Detected!"
                    statusSubtitle.text = "$suspiciousCount suspicious apps found"
                    statusTitle.setTextColor(getColor(R.color.neon_red))
                } else {
                    statusTitle.text = "System Secure"
                    statusSubtitle.text = "No threats detected"
                    statusTitle.setTextColor(getColor(R.color.safe_green))
                }
                Toast.makeText(this@MainActivity, "Scan Complete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProtectionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "security_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Security Alerts",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle("Qurilma himoya ostida")
            .setContentText("Real-time monitoring faol")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        notificationManager.notify(1, notification)
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

    private fun updateStatusView(isProtected: Boolean = true) { 
        if (isProtected) {
            statusTitle.text = "System Secure"
            statusSubtitle.text = "Real-time protection is active."
            statusTitle.setTextColor(getColor(R.color.white))
            findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.shield_animation).resumeAnimation()
        } else {
            statusTitle.text = "Protection Paused"
            statusSubtitle.text = "Enable protection to stay safe."
            statusTitle.setTextColor(getColor(R.color.neon_red))
            findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.shield_animation).pauseAnimation()
        }
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
