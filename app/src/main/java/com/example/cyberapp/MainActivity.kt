package com.example.cyberapp

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.example.cyberapp.modules.session_inspector.SessionInspectorActivity
import com.example.cyberapp.modules.url_inspector.UrlScanActivity
import com.example.cyberapp.utils.AnimUtils
import com.google.android.material.switchmaterial.SwitchMaterial
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
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var anomalyAdapter: AnomalyAdapter
    private val anomalyList = mutableListOf<Anomaly>()
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var lockOverlay: FrameLayout
    private lateinit var encryptedLogger: EncryptedLogger
    private lateinit var pinManager: PinManager
    private lateinit var securityManager: SecurityManager

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BODY_SENSORS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BODY_SENSORS
        )
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startLoggerService()
        } else {
            Toast.makeText(this, getString(R.string.permissions_denied_warning), Toast.LENGTH_LONG).show()
            findViewById<SwitchMaterial>(R.id.switch_protection).isChecked = false
        }
    }

    private val pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lockOverlay.isGone = true
            Toast.makeText(this, getString(R.string.welcome_back), Toast.LENGTH_SHORT).show()
        } else {
            if (lockOverlay.isVisible) {
                finish()
            }
        }
    }

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent(this, CyberVpnService::class.java)
            intent.action = CyberVpnService.ACTION_CONNECT
            startService(intent)
            CyberVpnService.isRunning = true
            prefs.edit().putBoolean("vpn_running", true).apply()
            
            // Update UI
            updateVpnUi()
            
            Toast.makeText(this, getString(R.string.vpn_connected), Toast.LENGTH_SHORT).show()
            showProtectionNotification()
        } else {
            Toast.makeText(this, getString(R.string.vpn_permission_denied), Toast.LENGTH_LONG).show()
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
        
        lifecycleScope.launch(Dispatchers.Main) {
            updateAnomaliesView()
            // Security checks
            if (!BuildConfig.DEBUG) {
                performSecurityChecks()
            }
            checkRootStatus()
            authenticateUser()
        }
    }
    
    private suspend fun performSecurityChecks() = withContext(Dispatchers.IO) {
        val securityCheck = securityManager.performSecurityCheck()
        if (securityCheck.isDebuggerAttached || securityCheck.isApkTampered) {
            withContext(Dispatchers.Main) {
                showSecurityThreatDialog(securityCheck)
            }
        }
    }
    
    private fun showSecurityThreatDialog(securityCheck: SecurityCheckResult) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.security_alert))
            .setMessage(getString(R.string.critical_threat_detected, securityCheck.getThreatDescription()))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.exit)) { _, _ -> finish() }
            .show()
    }
    
    private fun authenticateUser() {
        if (!pinManager.isPinSet()) {
            lockOverlay.isGone = true
            return
        }

        if (biometricManager.canAuthenticate()) {
            biometricManager.authenticate(
                onSuccess = { lockOverlay.isGone = true },
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
        val actionUrlScan = findViewById<CardView>(R.id.action_url_scan)
        val actionSession = findViewById<CardView>(R.id.action_session)
        val actionApps = findViewById<CardView>(R.id.action_apps)
        val actionPermissions = findViewById<CardView>(R.id.action_permissions)
        val settingsButton = findViewById<ImageView>(R.id.settings_icon) // Changed from settings_button to settings_icon

        // Apply Animations
        AnimUtils.applyScaleAnimation(btnQuickScan)
        AnimUtils.applyScaleAnimation(actionUrlScan)
        AnimUtils.applyScaleAnimation(actionSession)
        AnimUtils.applyScaleAnimation(actionApps)
        AnimUtils.applyScaleAnimation(actionPermissions)
        AnimUtils.applyScaleAnimation(settingsButton)

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
            startActivity(Intent(this, SessionInspectorActivity::class.java))
        }
        
        actionApps.setOnClickListener {
            vibrateDevice()
            startActivity(Intent(this, AppAnalysisActivity::class.java))
        }
        
        actionPermissions.setOnClickListener {
            vibrateDevice()
            val intent = Intent(this, AppAnalysisActivity::class.java)
            intent.putExtra("TITLE", getString(R.string.permission_manager))
            startActivity(intent)
        }

        settingsButton.setOnClickListener { 
            vibrateDevice()
            startActivity(Intent(this, SettingsActivity::class.java)) 
        }

        // Unlock Button
        findViewById<AppCompatButton>(R.id.btn_unlock).setOnClickListener {
            vibrateDevice()
            authenticateUser()
        }
        
        setupVpnToggle()
        setupProtectionSwitch()
    }

    private fun setupProtectionSwitch() {
        val switchProtection = findViewById<SwitchMaterial>(R.id.switch_protection)
        
        // Initial State Check
        switchProtection.isChecked = LoggerService.isRunning
        updateStatusView(LoggerService.isRunning)

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            vibrateDevice()
            if (isChecked) {
                checkAndRequestPermissions()
            } else {
                stopLoggerService()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startLoggerService()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startLoggerService() {
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            Toast.makeText(this, getString(R.string.please_grant_permission_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            findViewById<SwitchMaterial>(R.id.switch_protection).isChecked = false
            return
        }
        
        val serviceIntent = Intent(this, LoggerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, getString(R.string.protection_activated), Toast.LENGTH_SHORT).show()
        updateStatusView(true)
        prefs.edit().putBoolean("protection_enabled", true).apply()
    }

    private fun stopLoggerService() {
        val serviceIntent = Intent(this, LoggerService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, getString(R.string.protection_stopped), Toast.LENGTH_SHORT).show()
        updateStatusView(false)
        prefs.edit().putBoolean("protection_enabled", false).apply()
    }

    private fun setupVpnToggle() {
        val actionVpn = findViewById<CardView>(R.id.action_vpn)
        
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
                Toast.makeText(this, getString(R.string.vpn_disconnected), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, getString(R.string.vpn_connected), Toast.LENGTH_SHORT).show()
                        showProtectionNotification()
                    }
                } catch (e: Exception) {
                    Log.e(tag, getString(R.string.vpn_start_failed, e.message))
                    Toast.makeText(this, getString(R.string.vpn_error, e.message), Toast.LENGTH_LONG).show()
                    // If SecurityException, it might be a system bug or UID mismatch.
                    // We can try to reset the VPN state or just inform the user.
                    prefs.edit().putBoolean("vpn_running", false).apply()
                    updateVpnUi()
                }
            }
        }
    }
    
    private fun updateVpnUi() {
        val iconVpn = findViewById<ImageView>(R.id.icon_vpn)
        val textVpnStatus = findViewById<TextView>(R.id.text_vpn_status)
        val iconVpnToggle = findViewById<ImageView>(R.id.icon_vpn_toggle)
        val isVpnRunning = prefs.getBoolean("vpn_running", false)
        if (isVpnRunning) {
            iconVpn.setColorFilter(getColor(R.color.safe_green))
            textVpnStatus.text = getString(R.string.vpn_active_passive_mode)
            textVpnStatus.setTextColor(getColor(R.color.safe_green))
            iconVpnToggle.setImageResource(R.drawable.ic_check)
            iconVpnToggle.setColorFilter(getColor(R.color.safe_green))
        } else {
            iconVpn.setColorFilter(getColor(R.color.text_secondary))
            textVpnStatus.text = getString(R.string.tap_to_activate)
            textVpnStatus.setTextColor(getColor(R.color.text_secondary))
            iconVpnToggle.setImageResource(R.drawable.ic_arrow_right)
            iconVpnToggle.setColorFilter(getColor(R.color.text_secondary))
        }
    }

    private fun performQuickScan() {
        statusTitle.text = getString(R.string.scanning)
        statusSubtitle.text = getString(R.string.checking_system_security)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val rootDetector = RootDetector(this@MainActivity)
            val isRooted = if (BuildConfig.DEBUG) false else rootDetector.isRooted()

            val securityCheck = securityManager.performSecurityCheck()
            val isSecure = if (BuildConfig.DEBUG) true else !securityCheck.hasThreat()

            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            var suspiciousCount = 0
            for (pkg in packages) {
                if ((pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    val result = PhishingDetector.analyzePackage(this@MainActivity, pkg.packageName)
                    if (result.isSuspicious) suspiciousCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                if (isRooted || !isSecure || suspiciousCount > 0) {
                    statusTitle.text = getString(R.string.threats_detected)
                    var message = ""
                    if (isRooted) message += getString(R.string.root_jailbreak_detected)
                    if (!isSecure) message += "${securityCheck.getThreatDescription()}\n"
                    if (suspiciousCount > 0) message += getString(R.string.suspicious_apps_found, suspiciousCount)
                    statusSubtitle.text = message
                    statusTitle.setTextColor(getColor(R.color.neon_red))
                } else {
                    statusTitle.text = getString(R.string.system_secure)
                    statusSubtitle.text = getString(R.string.no_threats_detected)
                    statusTitle.setTextColor(getColor(R.color.safe_green))
                }
                Toast.makeText(this@MainActivity, getString(R.string.scan_complete), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProtectionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "security_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.security_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(getString(R.string.device_protected))
            .setContentText(getString(R.string.real_time_monitoring_active))
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
            Toast.makeText(this, getString(R.string.exception_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { 
            Log.e(tag, getString(R.string.failed_to_mark_as_normal, e.message))
        } 
    }

    override fun onBlockIp(ip: String) {
        try {
            // Initialize DomainBlocklist with context if not already initialized
            com.example.cyberapp.network.DomainBlocklist.init(this)
            
            // Add IP to blocklist
            val success = com.example.cyberapp.network.DomainBlocklist.add(ip)
            
            if (success) {
                Toast.makeText(this, getString(R.string.ip_blocked_successfully, ip), Toast.LENGTH_SHORT).show()
                Log.d(tag, "IP blocked successfully: $ip")
            } else {
                // IP might already be blocked or invalid format
                if (com.example.cyberapp.network.DomainBlocklist.isBlocked(ip)) {
                    Toast.makeText(this, getString(R.string.ip_already_blocked, ip), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.ip_block_failed, ip), Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to block IP: ${e.message}", e)
            Toast.makeText(this, getString(R.string.ip_block_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onUninstallApp(packageName: String) {
        try {
            // Check if package exists
            val packageManager = packageManager
            try {
                packageManager.getPackageInfo(packageName, 0)
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                Toast.makeText(this, getString(R.string.app_not_found, packageName), Toast.LENGTH_SHORT).show()
                return
            }
            
            // Check if trying to uninstall our own app (prevent self-destruction)
            if (packageName == this.packageName) {
                Toast.makeText(this, getString(R.string.cannot_uninstall_self), Toast.LENGTH_LONG).show()
                return
            }
            
            // Show confirmation dialog
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.uninstall_app_title))
                .setMessage(getString(R.string.uninstall_app_confirmation, packageName))
                .setPositiveButton(getString(R.string.uninstall)) { _, _ ->
                    // Launch uninstall intent
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                        Toast.makeText(this@MainActivity, getString(R.string.uninstalling_app, packageName), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to launch uninstall intent: ${e.message}", e)
                        Toast.makeText(this@MainActivity, getString(R.string.uninstall_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(getString(R.string.settings_cancel), null)
                .show()
        } catch (e: Exception) {
            Log.e(tag, "Failed to uninstall app: ${e.message}", e)
            Toast.makeText(this, getString(R.string.uninstall_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStatusView(isProtected: Boolean = true) { 
        val shieldAnimation = findViewById<LottieAnimationView>(R.id.shield_animation)
        if (isProtected) {
            statusTitle.text = getString(R.string.system_secure)
            statusSubtitle.text = getString(R.string.real_time_protection_active)
            statusTitle.setTextColor(getColor(R.color.white))
            shieldAnimation.resumeAnimation()
        } else {
            statusTitle.text = getString(R.string.protection_paused)
            statusSubtitle.text = getString(R.string.enable_protection_to_stay_safe)
            statusTitle.setTextColor(getColor(R.color.neon_red))
            shieldAnimation.pauseAnimation()
        }
    }

    companion object {
        private const val MAX_ANOMALIES_DISPLAY = 10
    }

    private suspend fun updateAnomaliesView() = withContext(Dispatchers.IO) { 
        val newAnomalies = mutableListOf<Anomaly>()
        val logContent = encryptedLogger.readLog(logFileName)
        
        if (logContent.isNotEmpty()) { 
            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            try { 
                logContent.lineSequence().take(MAX_ANOMALIES_DISPLAY).forEach { line -> // Limit to last MAX_ANOMALIES_DISPLAY
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
                    } catch (_: Exception) { } 
                } 
            } catch (_: Exception) { } 
        }
        
        withContext(Dispatchers.Main) { 
            val previousSize = anomalyList.size
            anomalyList.clear()
            anomalyAdapter.notifyItemRangeRemoved(0, previousSize)
            if (newAnomalies.isNotEmpty()) { 
                anomalyList.addAll(newAnomalies)
                anomalyAdapter.notifyItemRangeInserted(0, newAnomalies.size)
            }
        } 
    }

    private suspend fun checkRootStatus() = withContext(Dispatchers.IO) {
        val rootDetector = RootDetector(this@MainActivity)
        if (rootDetector.isRooted()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, getString(R.string.root_detected_security_compromised), Toast.LENGTH_LONG).show()
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
