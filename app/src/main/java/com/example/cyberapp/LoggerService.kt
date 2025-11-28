package com.example.cyberapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File
import java.util.Timer
import java.util.TimerTask
import kotlin.math.pow
import kotlin.math.sqrt

class LoggerService : Service(), SensorEventListener {

    private val TAG = "LoggerService"
    private val LOG_FILE_NAME = "behaviour_logs.jsonl"
    private val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024
    private val FOREGROUND_CHANNEL_ID = "CyberAppLoggerChannel"
    private val ANOMALY_CHANNEL_ID = "CyberAppAnomalyChannel"
    private val PERMISSION_CHANNEL_ID = "CyberAppPermissionChannel"
    private val FOREGROUND_NOTIFICATION_ID = 1
    private val AGGREGATION_INTERVAL_MS: Long = 60 * 1000
    private val PERMISSION_NOTIFICATION_THROTTLE_MS: Long = 60 * 60 * 1000 // 1 hour

    private lateinit var sensorManager: SensorManager
    private lateinit var prefs: EncryptedPrefsManager
    private val accelValues = mutableListOf<Double>()
    private val gyroValues = mutableListOf<Double>()
    private var timer: Timer? = null
    private var isPruning = false
    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: Any? = null // TelephonyCallback for Android 12+
    private var defaultDialerPackage: String? = null
    private var callPatrolTimer: Timer? = null
    private var networkMonitorTimer: Timer? = null
    private var networkStatsHelper: NetworkStatsHelper? = null
    private lateinit var encryptedLogger: EncryptedLogger
    private var lastPermissionNotificationTime: Long = 0

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val eventName = when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    setupSensors() // Resume sensors
                    if (BuildConfig.DEBUG) Log.d(TAG, "Screen ON: Sensors resumed")
                    "SCREEN_ON"
                }
                Intent.ACTION_SCREEN_OFF -> {
                    sensorManager.unregisterListener(this@LoggerService) // Pause sensors
                    if (BuildConfig.DEBUG) Log.d(TAG, "Screen OFF: Sensors paused to save battery")
                    "SCREEN_OFF"
                }
                else -> return
            }
            writeToFile("{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"EVENT\", \"name\":\"$eventName\"}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = EncryptedPrefsManager(this)
        createNotificationChannels()
        registerReceiver(screenStateReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        setupSensors()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager?
            defaultDialerPackage = telecomManager?.defaultDialerPackage
        }
        setupPhoneStateListener()
        
        // Initialize Network Monitoring
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            networkStatsHelper = NetworkStatsHelper(this)
        }
        
        // Initialize Encrypted Logger
        encryptedLogger = EncryptedLogger(this)
        // Migrate old plain text logs if they exist
        encryptedLogger.migratePlainTextLog(LOG_FILE_NAME)
    }

    override fun onDestroy() {
        super.onDestroy()
        writeToFile("{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"SERVICE_STATUS\", \"status\":\"STOPPED\"}")
        timer?.cancel()
        callPatrolTimer?.cancel()
        networkMonitorTimer?.cancel()
        sensorManager.unregisterListener(this)
        unregisterReceiver(screenStateReceiver)
        
        // Cleanup telephony listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                try {
                    telephonyManager.unregisterTelephonyCallback(it as android.telephony.TelephonyCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering TelephonyCallback: ${e.message}")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                telephonyManager.listen(null, android.telephony.PhoneStateListener.LISTEN_NONE)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping PhoneStateListener: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkLearningModeAndCreateProfile()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        startAggregationTimer()
        startNetworkMonitoring()
        return START_STICKY
    }
    
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun setupPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) uchun TelephonyCallback
            try {
                val callback = object : android.telephony.TelephonyCallback(), android.telephony.TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                if (BuildConfig.DEBUG) Log.d(TAG, "TelephonyCallback registered (Android 12+)")
            } catch (e: SecurityException) {
                Log.e(TAG, "READ_PHONE_STATE ruxsati berilmagan!")
            }
        } else {
            // Android 11 va undan past versiyalar uchun PhoneStateListener (deprecated)
            @Suppress("DEPRECATION")
            try {
                val listener = object : android.telephony.PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                        handleCallState(state)
                    }
                }
                telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                if (BuildConfig.DEBUG) Log.d(TAG, "PhoneStateListener registered (Android 11-)")
            } catch (e: SecurityException) {
                Log.e(TAG, "READ_PHONE_STATE ruxsati berilmagan!")
            }
        }
    }

    private fun handleCallState(state: Int) {
        val eventName = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "CALL_IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "CALL_RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "CALL_OFFHOOK"
            else -> "CALL_UNKNOWN"
        }
        writeToFile("{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"PHONE_STATE\", \"state\":\"$eventName\"}")
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) startCallPatrol()
        else if (state == TelephonyManager.CALL_STATE_IDLE) stopCallPatrol()
    }

    private fun startAggregationTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val foregroundApp = getForegroundApp()
                checkAnomalyUsingProfile(foregroundApp)
                aggregateAndLogSensorData(foregroundApp)
            }
        }, 15000, AGGREGATION_INTERVAL_MS)
    }

    private fun startCallPatrol() {
        stopCallPatrol()
        callPatrolTimer = Timer()
        callPatrolTimer?.scheduleAtFixedRate(object : TimerTask() { override fun run() { checkActivityDuringCall() } }, 5000, 10000)
        if (BuildConfig.DEBUG) Log.d(TAG, "SUHBAT PATRULI ISHGA TUSHDI!")
    }

    private fun stopCallPatrol() {
        callPatrolTimer?.cancel()
        callPatrolTimer = null
        if (BuildConfig.DEBUG) Log.d(TAG, "SUHBAT PATRULI TO'XTATILDI.")
    }

    private fun checkActivityDuringCall() {
        val foregroundApp = getForegroundApp() ?: return
        val topApps = prefs.getStringSet("topAppsProfile", emptySet()) ?: emptySet()
        val isSuspicious = foregroundApp != defaultDialerPackage && !topApps.contains(foregroundApp) && foregroundApp != packageName
        if (isSuspicious) {
            val anomalyDetails = "Suhbat paytida begona '$foregroundApp' ilovasi faollashdi! Bu josuslikka urinish bo'lishi mumkin."
            val exceptionKey = "exception_" + anomalyDetails.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")
            if (!prefs.getBoolean(exceptionKey, false)) {
                val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY\", \"description\":\"$anomalyDetails\", \"app\":\"$foregroundApp\"}"
                sendActiveDefenseNotification(anomalyDetails, foregroundApp, anomalyJson)
                stopCallPatrol()
            }
        }
    }

    private fun startNetworkMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (networkStatsHelper == null) return
        
        networkMonitorTimer?.cancel()
        networkMonitorTimer = Timer()
        networkMonitorTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkNetworkUsage()
            }
        }, 10000, 60000) // Check every 60 seconds
        if (BuildConfig.DEBUG) Log.d(TAG, "Network monitoring started")
    }

    private fun checkNetworkUsage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val helper = networkStatsHelper ?: return
        
        // Check if PACKAGE_USAGE_STATS permission is granted
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            handleMissingUsageStatsPermission()
            return
        }
        
        try {
            val currentUsage = helper.getAppNetworkUsage(60000) ?: return
            
            // Log network usage
            val usageJson = "{\"timestamp\":${currentUsage.timestamp}, \"type\":\"NETWORK_USAGE\", \"rx_bytes\":${currentUsage.rxBytes}, \"tx_bytes\":${currentUsage.txBytes}}"
            writeToFile(usageJson)
            prefs.edit()
                .putLong("last_network_rx_bytes", currentUsage.rxBytes)
                .putLong("last_network_tx_bytes", currentUsage.txBytes)
                .putLong("last_network_timestamp", currentUsage.timestamp)
                .apply()
            broadcastNetworkStats(currentUsage.rxBytes, currentUsage.txBytes)
            
            // Check for anomalies if profile exists
            if (prefs.getBoolean("isProfileCreated", false)) {
                val baselineRx = prefs.getLong("baseline_network_rx", 0L)
                val baselineTx = prefs.getLong("baseline_network_tx", 0L)
                
                if (helper.isAnomalousUsage(currentUsage, baselineRx, baselineTx, getSensitivityMultiplier())) {
                    val anomalyDetails = "Tarmoq trafigi anomal: RX=${helper.formatBytes(currentUsage.rxBytes)}, TX=${helper.formatBytes(currentUsage.txBytes)}"
                    val exceptionKey = "exception_network_anomaly"
                    
                    if (!prefs.getBoolean(exceptionKey, false)) {
                        val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY_NETWORK\", \"description\":\"$anomalyDetails\", \"rx\":${currentUsage.rxBytes}, \"tx\":${currentUsage.txBytes}}"
                        sendActiveDefenseNotification(anomalyDetails, "Network", anomalyJson)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network monitoring error: ${e.message}")
        }
    }
    
    @android.annotation.SuppressLint("MissingPermission")
    private fun handleMissingUsageStatsPermission() {
        val currentTime = System.currentTimeMillis()
        
        // Throttle notifications - max 1 per hour
        if (currentTime - lastPermissionNotificationTime < PERMISSION_NOTIFICATION_THROTTLE_MS) {
            return
        }
        
        lastPermissionNotificationTime = currentTime
        
        // Log warning
        Log.w(TAG, "PACKAGE_USAGE_STATS permission not granted - network monitoring paused")
        writeToFile("{\"timestamp\":$currentTime, \"type\":\"WARNING\", \"message\":\"Usage stats permission missing\"}")
        
        // Create notification
        val settingsIntent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val settingsPendingIntent = PendingIntent.getActivity(
            this,
            2,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        if (!canPostNotifications()) {
            return
        }

        val notification = NotificationCompat.Builder(this, PERMISSION_CHANNEL_ID)
            .setContentTitle("⚠️ Monitoring To'xtatildi")
            .setContentText("Tarmoq monitoringi uchun 'Usage Access' ruxsati kerak")
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(getColor(R.color.cyber_alert))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("CyberApp tarmoq trafigini kuzatish uchun 'Usage Access' ruxsatiga muhtoj. Sozlamalarga o'tib, CyberApp uchun ruxsat bering."))
            .addAction(0, "Ruxsat Berish", settingsPendingIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(this).notify(3, notification)
    }

    private fun broadcastNetworkStats(rxBytes: Long, txBytes: Long) {
        val intent = Intent(ACTION_NETWORK_STATS_UPDATE).apply {
            setPackage(packageName) // ✅ Correctly restricts to own package
            putExtra(EXTRA_RX_BYTES, rxBytes)
            putExtra(EXTRA_TX_BYTES, txBytes)
        }
        sendBroadcast(intent)
    }

    private fun checkLearningModeAndCreateProfile() {
        val isProfileCreated = prefs.getBoolean("isProfileCreated", false)
        if (isProfileCreated) return
        val firstLaunchTime = prefs.getLong("firstLaunchTime", 0L)
        if (firstLaunchTime == 0L) {
            prefs.edit().putLong("firstLaunchTime", System.currentTimeMillis()).apply()
            return
        }
        val learningPeriodDays = prefs.getLong("learningPeriodDays", 3L)
        val learningPeriodMs = learningPeriodDays * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - firstLaunchTime > learningPeriodMs) {
            createStatisticalProfileFromLogs()
        }
    }

    private fun createStatisticalProfileFromLogs() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Statistik profil yaratilmoqda...")
        
        // Read encrypted log
        val logContent = encryptedLogger.readLog(LOG_FILE_NAME)
        if (logContent.isEmpty()) return
        
        val appSensorData = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
        val appNetworkData = mutableMapOf<String, MutableSet<String>>()
        val networkUsageValues = mutableListOf<Pair<Long, Long>>()
        
        try {
            logContent.lines().forEach { line ->
                try {
                    val json = JSONObject(line)
                    when (json.getString("type")) {
                        "DATA_POINT" -> {
                            val app = json.optString("foreground_app", "unknown")
                            if (app != "unknown") {
                                if (!appSensorData.containsKey(app)) appSensorData[app] = mutableListOf()
                                appSensorData[app]?.add(Pair(json.getDouble("accel_variance"), json.getDouble("gyro_variance")))
                            }
                        }
                        "DATA_NETWORK" -> {
                            val app = json.optString("app", "unknown")
                            if (app != "unknown") {
                                if (!appNetworkData.containsKey(app)) appNetworkData[app] = mutableSetOf()
                                appNetworkData[app]?.add(json.getString("dest_ip"))
                            }
                        }
                        "NETWORK_USAGE" -> {
                            val rx = json.optLong("rx_bytes", -1)
                            val tx = json.optLong("tx_bytes", -1)
                            if (rx >= 0 && tx >= 0) {
                                networkUsageValues.add(Pair(rx, tx))
                            }
                        }
                    }
                } catch (e: Exception) { /* Ignore malformed JSON */ }
            }
            
            val editor = prefs.edit()
            val topApps = appSensorData.keys.union(appNetworkData.keys)
                .associateWith { (appSensorData[it]?.size ?: 0) + (appNetworkData[it]?.size ?: 0) }
                .entries.sortedByDescending { it.value }
                .take(5).map { it.key }
                
            editor.putStringSet("topAppsProfile", topApps.toSet())
            
            topApps.forEach { appName ->
                appSensorData[appName]?.let { data ->
                    val (accelMean, accelStdDev) = calculateMeanAndStdDev(data.map { it.first })
                    editor.putFloat("profile_app_${appName}_accel_mean", accelMean.toFloat())
                    editor.putFloat("profile_app_${appName}_accel_stddev", accelStdDev.toFloat())
                }
                appNetworkData[appName]?.let {
                    editor.putStringSet("profile_app_${appName}_ips", it)
                }
            }

            if (networkUsageValues.isNotEmpty()) {
                val avgRx = networkUsageValues.map { it.first }.average().toLong().coerceAtLeast(1L)
                val avgTx = networkUsageValues.map { it.second }.average().toLong().coerceAtLeast(1L)
                editor.putLong("baseline_network_rx", avgRx)
                editor.putLong("baseline_network_tx", avgTx)
            }
            
            editor.putBoolean("isProfileCreated", true)
            editor.putLong("profileCreationTime", System.currentTimeMillis())
            editor.apply()
            if (BuildConfig.DEBUG) Log.d(TAG, "Yagona statistik profil yaratildi.")
            
        } catch (e: Exception) { 
            Log.e(TAG, "Statistik profil yaratishda xatolik: ${e.message}") 
        }
    }

    private fun checkAnomalyUsingProfile(foregroundApp: String?) {
        foregroundApp ?: return
        if (!prefs.getBoolean("isProfileCreated", false)) return
        val thresholdMultiplier = getSensitivityMultiplier().toDouble()
        val topApps = prefs.getStringSet("topAppsProfile", emptySet())
        var isAnomaly = false
        var anomalyDetails = ""
        if (topApps?.contains(foregroundApp) == true) {
            val mean = prefs.getFloat("profile_app_${foregroundApp}_accel_mean", -1f).toDouble()
            val stdDev = prefs.getFloat("profile_app_${foregroundApp}_accel_stddev", -1f).toDouble()
            if (mean != -1.0) {
                val currentAccel = if (accelValues.isNotEmpty()) accelValues.average() else 0.0
                val threshold = mean + thresholdMultiplier * stdDev
                if (currentAccel > threshold && threshold > 0.1) {
                    isAnomaly = true
                    anomalyDetails = "$foregroundApp odatdagidan keskin faol harakatda ishlatildi."
                }
            }
        } else {
            isAnomaly = true
            anomalyDetails = "Kam ishlatiladigan '$foregroundApp' ilovasi ochildi."
        }
        if (isAnomaly) {
            val exceptionKey = "exception_" + anomalyDetails.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")
            if (prefs.getBoolean(exceptionKey, false)) return
            val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY\", \"description\":\"$anomalyDetails\", \"app\":\"$foregroundApp\"}"
            sendActiveDefenseNotification(anomalyDetails, foregroundApp, anomalyJson)
        }
    }

    private fun writeToFile(text: String) {
        try {
            encryptedLogger.appendLog(LOG_FILE_NAME, text + "\n")
            
            // Check file size and prune if needed
            if (encryptedLogger.getLogSize(LOG_FILE_NAME) > MAX_LOG_SIZE_BYTES && !isPruning) {
                pruneLogFile()
            }
        } catch (e: Exception) { Log.e(TAG, "File write error: ${e.message}") }
    }

    private fun pruneLogFile() {
        isPruning = true
        try {
            // Read encrypted log
            val content = encryptedLogger.readLog(LOG_FILE_NAME)
            if (content.isEmpty()) {
                isPruning = false
                return
            }
            
            // Split into lines and count
            val lines = content.lines()
            if (lines.isEmpty()) {
                isPruning = false
                return
            }
            
            // Keep last 75% of lines
            val linesToSkip = (lines.size * 0.25).toInt()
            val prunedContent = lines.drop(linesToSkip).joinToString("\n")
            
            // Write back encrypted
            encryptedLogger.writeLog(LOG_FILE_NAME, prunedContent, append = false)
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Log file pruned: ${lines.size} -> ${lines.size - linesToSkip} lines")
        } catch (e: Exception) {
            Log.e(TAG, "Error pruning log: ${e.message}")
        } finally {
            isPruning = false
        }
    }

    @Synchronized
    private fun aggregateAndLogSensorData(foregroundApp: String?) {
        val accelVariance = if (accelValues.isNotEmpty()) calculateVariance(accelValues) else 0.0
        val gyroVariance = if (gyroValues.isNotEmpty()) calculateVariance(gyroValues) else 0.0
        val dataJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"DATA_POINT\", \"foreground_app\":\"${foregroundApp ?: "unknown"}\", \"accel_variance\":$accelVariance, \"gyro_variance\":$gyroVariance}"
        writeToFile(dataJson)
        accelValues.clear()
        gyroValues.clear()
    }

    private fun getForegroundApp(): String? {
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            return null
        }
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
            stats?.filter { it.lastTimeUsed > time - 1000 * 60 }?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: SecurityException) {
            null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val magnitude = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)).toDouble()
        synchronized(this) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accelValues.add(magnitude)
                Sensor.TYPE_GYROSCOPE -> gyroValues.add(magnitude)
                else -> {}
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Majburiy, lekin ishlatilmaydi */ }

    private fun calculateMeanAndStdDev(values: List<Double>): Pair<Double, Double> {
        if (values.size < 2) return Pair(0.0, 0.0)
        val mean = values.average()
        val stdDev = sqrt(values.sumOf { (it - mean).pow(2) } / (values.size - 1))
        return Pair(mean, stdDev)
    }

    private fun calculateVariance(values: List<Double>): Double = calculateMeanAndStdDev(values).second.pow(2)

    private fun getSensitivityMultiplier(): Float {
        return when (prefs.getInt("sensitivityLevel", 1)) {
            0 -> 3.0f
            2 -> 1.5f
            else -> 2.0f
        }
    }

    private fun playAlertSound() {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
            // Signature "double beep"
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    toneGen.release()
                }, 450)
            }, 250)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(1000)

    @android.annotation.SuppressLint("MissingPermission")
    private fun sendActiveDefenseNotification(details: String, packageName: String, jsonLog: String) {
        writeToFile(jsonLog)

        if (!canPostNotifications()) {
            return
        }
        
        // Play signature alert sound
        playAlertSound()
        
        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        val uninstallPendingIntent = PendingIntent.getActivity(this, packageName.hashCode(), uninstallIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val uninstallAction = NotificationCompat.Action.Builder(0, "O'CHIRISH", uninstallPendingIntent).build()
        val detailsIntent = Intent(this, MainActivity::class.java)
        val detailsPendingIntent = PendingIntent.getActivity(this, 1, detailsIntent, PendingIntent.FLAG_IMMUTABLE)
        val detailsAction = NotificationCompat.Action.Builder(0, "Tafsilotlar", detailsPendingIntent).build()
        val notification = NotificationCompat.Builder(this, ANOMALY_CHANNEL_ID)
            .setContentTitle("⚠️ XAVF ANIQLANDI!")
            .setContentText(details)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(getColor(R.color.cyber_alert))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .addAction(uninstallAction)
            .addAction(detailsAction)
            .setAutoCancel(true)
            .build()
        
        val notificationId = notificationIdCounter.getAndIncrement()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("CyberApp Security")
            .setContentText("Qurilma himoya ostida.")
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(getColor(R.color.cyber_primary))
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // 1. Foreground Service Channel (Low Importance - Silent)
            val fChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, "Monitoring Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when the app is running in background"
            }
            
            // 2. General Anomaly Channel (High Importance - Sound/Vibrate)
            val aChannel = NotificationChannel(ANOMALY_CHANNEL_ID, "Behavioral Anomalies", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for unusual movement or app usage"
                enableVibration(true)
            }
            
            // 3. Critical Security Channel (Max Importance - Heads Up)
            val sChannel = NotificationChannel("CyberAppSecurityChannel", "Critical Security Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for potential intruders or spying attempts"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(listOf(fChannel, aChannel, sChannel))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_NETWORK_STATS_UPDATE = "com.example.cyberapp.NETWORK_STATS_UPDATE"
        const val EXTRA_RX_BYTES = "extra_rx_bytes"
        const val EXTRA_TX_BYTES = "extra_tx_bytes"
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
