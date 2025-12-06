package com.example.cyberapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.example.cyberapp.modules.url_inspector.UrlScanActivity
import com.example.cyberapp.network.DomainBlocklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), AnomalyAdapter.OnAnomalyInteractionListener {
    private val TAG = "MainActivity"
    private val LOG_FILE_NAME = "behaviour_logs.jsonl"
    private lateinit var anomaliesRecyclerView: RecyclerView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var anomalyAdapter: AnomalyAdapter
    private val anomalyList = mutableListOf<Anomaly>()
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var lockOverlay: android.widget.FrameLayout
    private lateinit var shieldAnimationView: LottieAnimationView

    private val prefs: EncryptedPrefsManager by lazy { EncryptedPrefsManager(this) }
    private val pinManager: PinManager by lazy { PinManager(this) }
    private val securityManager: SecurityManager by lazy { SecurityManager(this) }
    private val encryptedLogger: EncryptedLogger by lazy { EncryptedLogger(this) }
    
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
        if (permissions[android.Manifest.permission.READ_PHONE_STATE] == true) {
            // User can now re-try enabling protection
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusTitle = findViewById(R.id.status_title)
        statusSubtitle = findViewById(R.id.status_subtitle)
        lockOverlay = findViewById(R.id.lock_overlay)
        shieldAnimationView = findViewById(R.id.shield_animation)
        
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

        Handler(Looper.getMainLooper()).postDelayed({
            shieldAnimationView.playAnimation()
        }, 500) 
    }

    override fun onResume() {
        super.onResume()
        // Update UI when returning to the app
        setupVpnToggle() 
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

    private fun setupButtonsAndListeners() {
        val btnQuickScan = findViewById<Button>(R.id.btn_quick_scan)
        val actionUrlScan = findViewById<androidx.cardview.widget.CardView>(R.id.action_url_scan)
        val actionSession = findViewById<androidx.cardview.widget.CardView>(R.id.action_session)
        val actionApps = findViewById<androidx.cardview.widget.CardView>(R.id.action_apps)
        val actionPermissions = findViewById<androidx.cardview.widget.CardView>(R.id.action_permissions)
        val settingsButton = findViewById<ImageView>(R.id.settings_icon)

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
            startActivity(Intent(this, UrlScanActivity::class.java))
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
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_unlock).setOnClickListener { 
            vibrateDevice()
            authenticateUser()
        }
        
        setupVpnToggle()
        setupProtectionSwitch()
    }

    private fun setupProtectionSwitch() {
        val switchProtection = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_protection)
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
                if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
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
    
    private fun isInLearningMode(): Boolean {
        val startTime = prefs.getLong("learning_mode_start_timestamp", 0L)
        if (startTime == 0L) return false // Not even started
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - startTime) < sevenDaysInMillis
    }

    private fun setupVpnToggle() {
        val actionVpn = findViewById<androidx.cardview.widget.CardView>(R.id.action_vpn)
        val iconVpn = findViewById<ImageView>(R.id.icon_vpn)
        val textVpnStatus = findViewById<TextView>(R.id.text_vpn_status)
        val iconVpnToggle = findViewById<ImageView>(R.id.icon_vpn_toggle)

        fun updateVpnUi() {
            val isVpnRunning = CyberVpnService.isRunning
            if (isVpnRunning) {
                iconVpn.setColorFilter(getColor(R.color.safe_green))
                if(isInLearningMode()){
                    textVpnStatus.text = "O\'rganuvchi Rejim Faol"
                    textVpnStatus.setTextColor(getColor(R.color.yellow)) // Yellow for learning
                } else {
                    textVpnStatus.text = "Himoya Faol"
                    textVpnStatus.setTextColor(getColor(R.color.safe_green))
                }
                iconVpnToggle.setImageResource(R.drawable.ic_check)
                iconVpnToggle.setColorFilter(getColor(R.color.safe_green))
            } else {
                iconVpn.setColorFilter(getColor(R.color.text_secondary))
                textVpnStatus.text = "Faollashtirish uchun bosing"
                textVpnStatus.setTextColor(getColor(R.color.text_secondary))
                iconVpnToggle.setImageResource(R.drawable.ic_arrow_right)
                iconVpnToggle.setColorFilter(getColor(R.color.text_secondary))
            }
        }

        updateVpnUi()

        actionVpn.setOnClickListener {
            vibrateDevice()
            if (CyberVpnService.isRunning) {
                startService(Intent(this, CyberVpnService::class.java).setAction(CyberVpnService.ACTION_DISCONNECT))
            } else {
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) {
                    vpnLauncher.launch(vpnIntent)
                } else {
                    startService(Intent(this, CyberVpnService::class.java).setAction(CyberVpnService.ACTION_CONNECT))
                }
            }
            // UI will be updated in onResume
        }
    }
    
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService(Intent(this, CyberVpnService::class.java).setAction(CyberVpnService.ACTION_CONNECT))
        }
    }

    private fun performQuickScan() {
        statusTitle.text = "Scanning..."
        statusSubtitle.text = "Checking for threats..."
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val rootDetector = RootDetector(this@MainActivity)
                val isRooted = rootDetector.isRooted()
                val securityCheck = securityManager.performSecurityCheck()
                val pm = packageManager
                val packages = pm.getInstalledPackages(0)
                var suspiciousCount = 0
                for (pkg in packages) {
                    if ((pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                        val analysisResult = PhishingDetector.analyzePackage(this@MainActivity, pkg.packageName)
                        if (analysisResult.isSuspicious) {
                            suspiciousCount++
                        }
                    }
                }
                Triple(isRooted, securityCheck.isDebuggerAttached, suspiciousCount)
            }
            
            val (isRooted, isDebuggerAttached, suspiciousCount) = result
            if (isRooted || isDebuggerAttached || suspiciousCount > 0) {
                statusTitle.text = "Threats Detected!"
                statusSubtitle.text = if(suspiciousCount > 0) "$suspiciousCount suspicious apps found" else "Security compromised"
                statusTitle.setTextColor(getColor(R.color.neon_red))
            } else {
                statusTitle.text = "System Secure"
                statusSubtitle.text = "No threats detected"
                statusTitle.setTextColor(getColor(R.color.safe_green))
            }
            Toast.makeText(this@MainActivity, "Scan Complete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProtectionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
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

    override fun onBlockIp(ip: String) {
        DomainBlocklist.add(ip)
        Toast.makeText(this, "$ip blocked successfully", Toast.LENGTH_SHORT).show()
        updateAnomaliesView() // Refresh the list
    }

    override fun onUninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun updateStatusView(isProtected: Boolean = true) { 
        if (isProtected) {
            statusTitle.text = "System Secure"
            statusSubtitle.text = "Real-time protection is active."
            statusTitle.setTextColor(getColor(R.color.white))
            shieldAnimationView.resumeAnimation()
        } else {
            statusTitle.text = "Protection Paused"
            statusSubtitle.text = "Enable protection to stay safe."
            statusTitle.setTextColor(getColor(R.color.neon_red))
            shieldAnimationView.pauseAnimation()
        }
    }

    private fun updateAnomaliesView() { 
        lifecycleScope.launch { 
            val newAnomalies = withContext(Dispatchers.IO) {
                val anomalies = mutableListOf<Anomaly>()
                val logContent = encryptedLogger.readLog(LOG_FILE_NAME)
                
                if (logContent.isNotEmpty()) { 
                    val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                    try { 
                        logContent.lineSequence().toList().reversed().take(20).forEach { line ->
                            try { 
                                if (line.isNotEmpty()) {
                                    val json = JSONObject(line as String)
                                    val type = json.optString("type")
                                    if (type == "ANOMALY" || type == "ANOMALY_NETWORK") { 
                                        val description = json.optString("description")
                                        val exceptionKey = "exception_" + description.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")
                                        if (!prefs.getBoolean(exceptionKey, false)) {
                                             val timestamp = json.optLong("timestamp")
                                            anomalies.add(Anomaly(dateFormat.format(Date(timestamp as Long)), description, line))
                                        }
                                    } 
                                }
                            } catch (e: Exception) { } 
                        } 
                    } catch (e: Exception) { } 
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
                Toast.makeText(this@MainActivity, "ROOT DETECTED! Security compromised.", Toast.LENGTH_LONG).show()
            }
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
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}
