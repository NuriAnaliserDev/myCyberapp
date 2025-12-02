package com.example.cyberapp

import android.app.Activity
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
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
    private lateinit var statusTextView: TextView
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var anomalyAdapter: AnomalyAdapter
    private val anomalyList = mutableListOf<Anomaly>()
    private lateinit var vpnSwitch: MaterialSwitch
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var lockOverlay: android.widget.FrameLayout
    private lateinit var networkRxValue: TextView
    private lateinit var networkTxValue: TextView
    private lateinit var encryptedLogger: EncryptedLogger
    private var isNetworkReceiverRegistered = false
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastUpdateTime: Long = 0
    private val networkStatsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LoggerService.ACTION_NETWORK_STATS_UPDATE) {
                val rx = intent.getLongExtra(LoggerService.EXTRA_RX_BYTES, -1L)
                val tx = intent.getLongExtra(LoggerService.EXTRA_TX_BYTES, -1L)
                updateNetworkStatsUI(
                    rx.takeIf { it >= 0 },
                    tx.takeIf { it >= 0 }
                )
            }
        }
    }

    //<editor-fold desc="Lifecycle and Launchers">
    private val vpnAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Tarmoq himoyasi uchun ruxsat zarur!", Toast.LENGTH_LONG).show()
            vpnSwitch.isChecked = false
        }
    }

    private lateinit var pinManager: PinManager
    private lateinit var securityManager: SecurityManager
    
    private val pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lockOverlay.visibility = android.view.View.GONE
            Toast.makeText(this, "Xush kelibsiz!", Toast.LENGTH_SHORT).show()
        } else {
            // If cancelled or failed, keep locked or exit
            if (lockOverlay.visibility == android.view.View.VISIBLE) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusTextView = findViewById(R.id.status_textview)
        vpnSwitch = findViewById(R.id.vpn_switch)
        lockOverlay = findViewById(R.id.lock_overlay)
        networkRxValue = findViewById(R.id.network_rx_value)
        networkTxValue = findViewById(R.id.network_tx_value)
        
        findViewById<TextView>(R.id.app_version).text = "v${BuildConfig.VERSION_NAME}"
        
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
        
        
        // Sensor Graph Init (Commented out - removed from layout for cleaner design)
        // val chart = findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.sensor_chart)
        // graphManager = SensorGraphManager(chart)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

        setupRecyclerView()
        setupButtonsAndListeners()
        updateStatusView()
        updateAnomaliesView()
        updateNetworkStatsFromPrefs()
        
        // Security checks
        performSecurityChecks()
        
        // Root Detection
        checkRootStatus()
        
        authenticateUser()
    }
    
    private fun performSecurityChecks() {
        val securityCheck = securityManager.performSecurityCheck()
        
        // Log security status
        if (BuildConfig.DEBUG) android.util.Log.d("MainActivity", "Security Check: ${securityCheck.getThreatDescription()}")
        
        // Handle critical threats (debugger, tampering)
        if (securityCheck.isDebuggerAttached || securityCheck.isApkTampered) {
            showSecurityThreatDialog(securityCheck)
        }
        
        // Warn about emulator (non-critical)
        if (securityCheck.isEmulator && !securityCheck.isDebuggable) {
            Toast.makeText(this, "Emulator aniqlandi", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSecurityThreatDialog(securityCheck: SecurityCheckResult) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Xavfsizlik Tahdidi")
            .setMessage("Xavfli muhit aniqlandi:\n\n${securityCheck.getThreatDescription()}\n\nIlova xavfsizlik sababli yopiladi.")
            .setCancelable(false)
            .setPositiveButton("Tushundim") { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun authenticateUser() {
        if (!pinManager.isPinSet()) {
            // PIN o'rnatilmagan bo'lsa, to'g'ridan-to'g'ri kirishga ruxsat berish
            lockOverlay.visibility = android.view.View.GONE
            Toast.makeText(this, "Xush kelibsiz!", Toast.LENGTH_SHORT).show()
            return
        }

        if (biometricManager.canAuthenticate()) {
            biometricManager.authenticate(
                onSuccess = {
                    lockOverlay.visibility = android.view.View.GONE
                    Toast.makeText(this, "Xush kelibsiz!", Toast.LENGTH_SHORT).show()
                },
                onError = { 
                    launchPinFallback()
                },
                onFailed = {
                    launchPinFallback()
                }
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

    //<editor-fold desc="Setup and Listeners - FINAL VERSION">
    private fun setupButtonsAndListeners() {
        findViewById<Button>(R.id.start_logger_button).setOnClickListener { 
            vibrateDevice()
            startLogger() 
        }
        findViewById<Button>(R.id.stop_logger_button).setOnClickListener { 
            vibrateDevice()
            stopLogger() 
        }
        findViewById<Button>(R.id.settings_button).setOnClickListener { 
            vibrateDevice()
            startActivity(Intent(this, SettingsActivity::class.java)) 
        }
        findViewById<Button>(R.id.refresh_anomalies_button).setOnClickListener { 
            vibrateDevice()
            updateAnomaliesView() 
        }
        findViewById<Button>(R.id.reset_profile_button).setOnClickListener { 
            vibrateDevice()
            confirmAndResetProfile() 
        }
        findViewById<Button>(R.id.app_analysis_button).setOnClickListener { 
            vibrateDevice()
            startActivity(Intent(this, AppAnalysisActivity::class.java)) 
        }
        findViewById<Button>(R.id.btn_unlock).setOnClickListener { 
            vibrateDevice()
            authenticateUser() 
        }

        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                prepareAndStartVpn()
            } else {
                stopVpnService()
            }
        }
        
        checkPermissions()
        checkAndRequestUsageStatsPermission()
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(android.Manifest.permission.READ_PHONE_STATE)
        permissions.add(android.Manifest.permission.BODY_SENSORS)
        
        val missingPermissions = permissions.filter { 
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED 
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 1001)
        }
    }
    //</editor-fold>

    //<editor-fold desc="VPN Logic - FIXED">
    private fun prepareAndStartVpn() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnAuthLauncher.launch(vpnIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, CyberVpnService::class.java).apply {
            action = CyberVpnService.ACTION_CONNECT // TO'G'RILANDI
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        vpnSwitch.isChecked = true
    }

    private fun stopVpnService() {
        val intent = Intent(this, CyberVpnService::class.java).apply {
            action = CyberVpnService.ACTION_DISCONNECT // TO'G'RILANDI
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        vpnSwitch.isChecked = false
    }
    //</editor-fold>

    // ... (The rest of the MainActivity code is correct and unchanged)
    //<editor-fold desc="Unchanged Code">
    private fun setupRecyclerView() { anomaliesRecyclerView = findViewById(R.id.anomalies_recyclerview); anomalyAdapter = AnomalyAdapter(anomalyList, this); anomaliesRecyclerView.adapter = anomalyAdapter; anomaliesRecyclerView.layoutManager = LinearLayoutManager(this); }
    override fun onMarkAsNormal(anomaly: Anomaly, position: Int) { try { val json = JSONObject(anomaly.rawJson); val description = json.getString("description"); val exceptionKey = "exception_" + description.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), ""); prefs.edit().putBoolean(exceptionKey, true).apply(); anomalyList.removeAt(position); anomalyAdapter.notifyItemRemoved(position); anomalyAdapter.notifyItemRangeChanged(position, anomalyList.size); Toast.makeText(this, "Istisno saqlandi", Toast.LENGTH_SHORT).show(); } catch (e: Exception) { Log.e(TAG, "Failed to mark as normal: ${e.message}"); } }
    private fun confirmAndResetProfile() { AlertDialog.Builder(this).setTitle("Profilni O\'chirish").setMessage("Barcha o\'rganilgan ma\'lumotlarni o\'chirib, qayta o\'rganish rejimini boshlaysizmi?").setPositiveButton("Ha") { _, _ -> resetProfile() }.setNegativeButton("Yo\'q", null).show(); }
    private fun resetProfile() { stopLogger(); stopVpnService(); Toast.makeText(this, "Profil tozalanmoqda...", Toast.LENGTH_SHORT).show(); thread { prefs.edit().clear().apply(); File(filesDir, LOG_FILE_NAME).delete(); runOnUiThread { Toast.makeText(this, "Profil tozalandi. Ilovani qayta ishga tushiring.", Toast.LENGTH_LONG).show(); updateStatusView(); updateAnomaliesView(); } } }
    private fun startLogger() { val serviceIntent = Intent(this, LoggerService::class.java); androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent); }
    private fun stopLogger() { val serviceIntent = Intent(this, LoggerService::class.java); stopService(serviceIntent); }
    private fun updateStatusView() { val isProfileCreated = prefs.getBoolean("isProfileCreated", false); statusTextView.text = if (isProfileCreated) { val profileTime = prefs.getLong("profileCreationTime", 0); "Holat: Himoya faol (Profil: ${formatDate(profileTime)})" } else { val firstLaunch = prefs.getLong("firstLaunchTime", 0); if (firstLaunch == 0L) { prefs.edit().putLong("firstLaunchTime", System.currentTimeMillis()).apply() }; "Holat: O\'rganish rejimi..." } }
    private fun updateAnomaliesView() { 
        thread { 
            val newAnomalies = mutableListOf<Anomaly>()
            // FIX: Use EncryptedLogger to read logs, not direct File access
            val logContent = encryptedLogger.readLog(LOG_FILE_NAME)
            
            if (logContent.isNotEmpty()) { 
                val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                try { 
                    logContent.lineSequence().forEach { line -> 
                        try { 
                            if (line.isNotEmpty()) {
                                val json = JSONObject(line)
                                val type = json.getString("type")
                                if (type == "ANOMALY" || type == "ANOMALY_NETWORK") { 
                                    val description = json.getString("description")
                                    val exceptionKey = "exception_" + description.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")
                                    if (!prefs.getBoolean(exceptionKey, false)) { 
                                        val timestamp = json.getLong("timestamp")
                                        newAnomalies.add(Anomaly(dateFormat.format(Date(timestamp)), description, line))
                                    } 
                                } 
                            }
                        } catch (e: Exception) { Log.w(TAG, "Failed to parse log line: $line", e) } 
                    } 
                } catch (e: Exception) { 
                    Log.e(TAG, "Error parsing anomalies: ${e.message}") 
                } 
            }
            
            runOnUiThread { 
                anomalyList.clear()
                if (newAnomalies.isNotEmpty()) { 
                    anomalyList.addAll(newAnomalies.asReversed())
                }
                anomalyAdapter.notifyDataSetChanged()
            } 
        } 
    }
    private fun updateNetworkStatsFromPrefs() {
        val rx = prefs.getLong("last_network_rx_bytes", -1L)
        val tx = prefs.getLong("last_network_tx_bytes", -1L)
        updateNetworkStatsUI(rx.takeIf { it >= 0 }, tx.takeIf { it >= 0 })
    }
    private fun updateNetworkStatsUI(rxBytes: Long?, txBytes: Long?) {
        if (rxBytes != null && txBytes != null) {
            val currentTime = System.currentTimeMillis()
            val timeDiff = (currentTime - lastUpdateTime) / 1000.0 // seconds
            
            if (lastUpdateTime > 0 && timeDiff > 0) {
                val rxSpeed = ((rxBytes - lastRxBytes) / timeDiff).toLong()
                val txSpeed = ((txBytes - lastTxBytes) / timeDiff).toLong()
                
                networkRxValue.text = "${formatBytes(rxBytes)}\n↓ ${formatSpeed(rxSpeed)}"
                networkTxValue.text = "${formatBytes(txBytes)}\n↑ ${formatSpeed(txSpeed)}"
            } else {
                networkRxValue.text = formatBytes(rxBytes)
                networkTxValue.text = formatBytes(txBytes)
            }
            
            lastRxBytes = rxBytes
            lastTxBytes = txBytes
            lastUpdateTime = currentTime
        } else {
            networkRxValue.text = "--"
            networkTxValue.text = "--"
        }
    }
    private fun formatBytes(value: Long): String {
        return when {
            value < 1024 -> "$value B"
            value < 1024 * 1024 -> String.format("%.2f KB", value / 1024.0)
            value < 1024 * 1024 * 1024 -> String.format("%.2f MB", value / (1024.0 * 1024))
            else -> String.format("%.2f GB", value / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> String.format("%.2f KB/s", bytesPerSec / 1024.0)
            else -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024))
        }
    }
    private fun formatDate(timestamp: Long): String { return SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp)) }
    private fun checkAndRequestUsageStatsPermission() { if (!hasUsageStatsPermission()) { Toast.makeText(this, "Ilovalar statistikasini kuzatish uchun ruxsat bering", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } else { Toast.makeText(this, "Ruxsatnoma allaqachon berilgan!", Toast.LENGTH_SHORT).show(); } }
    private fun hasUsageStatsPermission(): Boolean { val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) } else { @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) }; return mode == AppOpsManager.MODE_ALLOWED;    }
    //</editor-fold>

    //<editor-fold desc="Root Detection">
    private fun checkRootStatus() {
        val rootDetector = RootDetector(this)
        if (rootDetector.isRooted()) {
            android.util.Log.w(TAG, "ROOT DETECTED!")
            if (BuildConfig.DEBUG) android.util.Log.d(TAG, rootDetector.getRootDetectionDetails())
            showRootWarningDialog()
        }
    }

    private fun showRootWarningDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_root_warning, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_exit).setOnClickListener {
            dialog.dismiss()
            finish() // Close app
        }

        val understandButton = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_understand)
        if (BuildConfig.DEBUG) {
            understandButton.setOnClickListener {
                dialog.dismiss()
                Toast.makeText(this, "Xavfsizlik kafolati berilmaydi!", Toast.LENGTH_LONG).show()
            }
        } else {
            understandButton.visibility = android.view.View.GONE
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    //</editor-fold>

    //<editor-fold desc="Sensor Graph Logic">
    private lateinit var sensorManager: android.hardware.SensorManager
    private var accelerometer: android.hardware.Sensor? = null
    private lateinit var graphManager: SensorGraphManager

    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            event?.let {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                // Calculate magnitude or just use one axis for visualization
                val magnitude = kotlin.math.sqrt(x*x + y*y + z*z) - 9.81f // Remove gravity
                graphManager.addEntry(magnitude.toFloat())
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(sensorListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isNetworkReceiverRegistered) {
            val filter = IntentFilter(LoggerService.ACTION_NETWORK_STATS_UPDATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    networkStatsReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                registerReceiver(networkStatsReceiver, filter)
            }
            isNetworkReceiverRegistered = true
        }
        updateNetworkStatsFromPrefs()
    }

    override fun onStop() {
        if (isNetworkReceiverRegistered) {
            unregisterReceiver(networkStatsReceiver)
            isNetworkReceiverRegistered = false
        }
        super.onStop()
    }
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

    //</editor-fold>
}
