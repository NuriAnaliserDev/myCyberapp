package com.example.cyberapp

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
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    private val FOREGROUND_NOTIFICATION_ID = 1
    private val AGGREGATION_INTERVAL_MS: Long = 60 * 1000

    private lateinit var sensorManager: SensorManager
    private lateinit var prefs: SharedPreferences
    private val accelValues = mutableListOf<Double>()
    private val gyroValues = mutableListOf<Double>()
    private var timer: Timer? = null
    private var isPruning = false
    private lateinit var telephonyManager: TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null
    private var defaultDialerPackage: String? = null
    private var callPatrolTimer: Timer? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val eventName = when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> "SCREEN_ON"
                Intent.ACTION_SCREEN_OFF -> "SCREEN_OFF"
                else -> return
            }
            writeToFile("{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"EVENT\", \"name\":\"$eventName\"}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("CyberAppPrefs", Context.MODE_PRIVATE)
        createNotificationChannels()
        registerReceiver(screenStateReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        setupSensors()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager?
            defaultDialerPackage = telecomManager?.defaultDialerPackage
        }
        setupPhoneStateListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        writeToFile("{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"SERVICE_STATUS\", \"status\":\"STOPPED\"}")
        timer?.cancel()
        callPatrolTimer?.cancel()
        sensorManager.unregisterListener(this)
        unregisterReceiver(screenStateReceiver)
        phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkLearningModeAndCreateProfile()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        startAggregationTimer()
        return START_STICKY
    }
    
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun setupPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
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
        }
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) { Log.e(TAG, "READ_PHONE_STATE ruxsati berilmagan!") }
    }

    private fun startAggregationTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val foregroundApp = getForegroundApp()
                checkAnomalyUsingProfile(foregroundApp)
                aggregateAndLogSensorData(foregroundApp)
                broadcastNetworkStats()
            }
        }, 15000, AGGREGATION_INTERVAL_MS)
    }

    private fun startCallPatrol() {
        stopCallPatrol()
        callPatrolTimer = Timer()
        callPatrolTimer?.scheduleAtFixedRate(object : TimerTask() { override fun run() { checkActivityDuringCall() } }, 5000, 10000)
        Log.d(TAG, "SUHBAT PATRULI ISHGA TUSHDI!")
    }

    private fun stopCallPatrol() {
        callPatrolTimer?.cancel()
        callPatrolTimer = null
        Log.d(TAG, "SUHBAT PATRULI TO'XTATILDI.")
    }

    private fun checkActivityDuringCall() {
        val foregroundApp = getForegroundApp() ?: return
        val topApps = prefs.getStringSet("topAppsProfile", emptySet()) ?: emptySet()
        val isSuspicious = foregroundApp != defaultDialerPackage && !topApps.contains(foregroundApp) && foregroundApp != packageName
        if (isSuspicious) {
            val anomalyDetails = "Suhbat paytida begona '$foregroundApp' ilovasi faollashdi! Bu josuslikka urinish bo\'lishi mumkin."
            val exceptionKey = "exception_" + anomalyDetails.replace(" ", "_").take(50)
            if (!prefs.getBoolean(exceptionKey, false)) {
                val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY\", \"description\":\"$anomalyDetails\", \"app\":\"$foregroundApp\"}"
                sendActiveDefenseNotification(anomalyDetails, foregroundApp, anomalyJson)
                stopCallPatrol()
            }
        }
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
        Log.d(TAG, "Statistik profil yaratilmoqda...")
        val logFile = File(filesDir, LOG_FILE_NAME)
        if (!logFile.exists() || logFile.length() == 0L) return
        val appSensorData = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
        val appNetworkData = mutableMapOf<String, MutableSet<String>>()
        try {
            logFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
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
                        }
                    } catch (e: Exception) { /* Ignore malformed JSON */ }
                }
            }
            val editor = prefs.edit()
            val topApps = appSensorData.keys.union(appNetworkData.keys).associateWith { (appSensorData[it]?.size ?: 0) + (appNetworkData[it]?.size ?: 0) }.entries.sortedByDescending { it.value }.take(5).map { it.key }
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
            editor.putBoolean("isProfileCreated", true)
            editor.putLong("profileCreationTime", System.currentTimeMillis())
            editor.apply()
            Log.d(TAG, "Yagona statistik profil yaratildi.")
        } catch (e: Exception) { Log.e(TAG, "Statistik profil yaratishda xatolik: ${e.message}") }
    }

    private fun checkAnomalyUsingProfile(foregroundApp: String?) {
        foregroundApp ?: return
        if (!prefs.getBoolean("isProfileCreated", false)) return
        val sensitivityLevel = prefs.getInt("sensitivityLevel", 1)
        val thresholdMultiplier = when (sensitivityLevel) { 0 -> 3.0; 2 -> 1.5; else -> 2.0 }
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
            val exceptionKey = "exception_" + anomalyDetails.replace(" ", "_").take(50)
            if (prefs.getBoolean(exceptionKey, false)) return
            val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY\", \"description\":\"$anomalyDetails\", \"app\":\"$foregroundApp\"}"
            sendActiveDefenseNotification(anomalyDetails, foregroundApp, anomalyJson)
        }
    }

    private fun writeToFile(text: String) {
        try {
            val file = File(filesDir, LOG_FILE_NAME)
            FileOutputStream(file, true).use { it.write((text + "\n").toByteArray()) }
            if (file.length() > MAX_LOG_SIZE_BYTES && !isPruning) {
                pruneLogFile(file)
            }
        } catch (e: Exception) { Log.e(TAG, "File write error: ${e.message}") }
    }

    private fun pruneLogFile(file: File) {
        isPruning = true
        val tempFile = File(filesDir, "$LOG_FILE_NAME.tmp")
        var linesCount = 0
        try {
            file.bufferedReader().use { r -> while (r.readLine() != null) linesCount++ }
            if (linesCount == 0) {
                isPruning = false
                return
            }
            val linesToSkip = (linesCount * 0.25).toInt()
            var currentLine = 0
            file.bufferedReader().use { r -> FileOutputStream(tempFile, false).use { w -> r.forEachLine { if (currentLine >= linesToSkip) w.write((it + "\n").toByteArray()); currentLine++ } } }
            if (!tempFile.renameTo(file)) {
                Log.e(TAG, "Log faylni almashtirib bo\'lmadi.")
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
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
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
        return stats?.filter { it.lastTimeUsed > time - 1000 * 60 }?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val magnitude = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)).toDouble()
        synchronized(this) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accelValues.add(magnitude)
                Sensor.TYPE_GYROSCOPE -> gyroValues.add(magnitude)
                else -> {} // Ignore other sensors
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

    private fun sendActiveDefenseNotification(details: String, packageName: String, jsonLog: String) {
        writeToFile(jsonLog)
        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        val uninstallPendingIntent = PendingIntent.getActivity(this, packageName.hashCode(), uninstallIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val uninstallAction = NotificationCompat.Action.Builder(0, "O'CHIRISH", uninstallPendingIntent).build()
        val detailsIntent = Intent(this, MainActivity::class.java)
        val detailsPendingIntent = PendingIntent.getActivity(this, 1, detailsIntent, PendingIntent.FLAG_IMMUTABLE)
        val detailsAction = NotificationCompat.Action.Builder(0, "Tafsilotlar", detailsPendingIntent).build()
        val notification = NotificationCompat.Builder(this, ANOMALY_CHANNEL_ID)
            .setContentTitle("⚠️ XAVF ANIQLANDI!")
            .setContentText(details)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .addAction(uninstallAction)
            .addAction(detailsAction)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createForegroundNotification(): Notification {
        // Use the channel created by NotificationHelper
        return NotificationCompat.Builder(this, com.example.cyberapp.utils.NotificationHelper.CHANNEL_ID_STATUS)
            .setContentTitle("NuriSafety: Faol")
            .setContentText("Qurilma himoya ostida.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        com.example.cyberapp.utils.NotificationHelper.createNotificationChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun broadcastNetworkStats() {
        try {
            val rxBytes = android.net.TrafficStats.getTotalRxBytes()
            val txBytes = android.net.TrafficStats.getTotalTxBytes()
            
            val intent = Intent(ACTION_NETWORK_STATS_UPDATE)
            intent.putExtra(EXTRA_RX_BYTES, rxBytes)
            intent.putExtra(EXTRA_TX_BYTES, txBytes)
            sendBroadcast(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to broadcast network stats: ${e.message}")
        }
    }

    companion object {
        const val ACTION_NETWORK_STATS_UPDATE = "com.example.cyberapp.action.NETWORK_STATS_UPDATE"
        const val EXTRA_RX_BYTES = "extra_rx_bytes"
        const val EXTRA_TX_BYTES = "extra_tx_bytes"
    }
}
