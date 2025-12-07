package com.example.cyberapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.cyberapp.network.DomainBlocklist
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class CyberVpnService : VpnService() {

    private val TAG = "CyberVpnService"
    private val LOG_FILE_NAME = "behaviour_logs.jsonl"
    private val ANOMALY_CHANNEL_ID = "CyberAppAnomalyChannel"
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var encryptedLogger: EncryptedLogger
    private lateinit var voiceAssistant: VoiceAssistant

    override fun onCreate() {
        super.onCreate()
        prefs = EncryptedPrefsManager(this)
        encryptedLogger = EncryptedLogger(this)
        voiceAssistant = VoiceAssistant(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) startVpn()
        if (intent?.action == ACTION_DISCONNECT) stopVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnThread?.isAlive == true) return
        isRunning = true

        // Start Learning Mode if it's the first time
        if (prefs.getLong("learning_mode_start_timestamp", 0L) == 0L) {
            prefs.edit().putLong("learning_mode_start_timestamp", System.currentTimeMillis()).apply()
        }

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }

        vpnThread = Thread {
            try {
                val builder = Builder()
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                    .setSession(getString(R.string.app_name))
                
                vpnInterface = builder.establish() ?: return@Thread

                val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
                val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)

                while (isRunning && !Thread.interrupted()) {
                    val bytesRead = vpnInput.read(buffer.array())
                    if (bytesRead > 0) {
                        buffer.limit(bytesRead)
                        val packet = buffer.duplicate()
                        
                        if (!shouldBlockPacket(packet)) {
                            parseAndProcessPacket(packet)
                            vpnOutput.write(packet.array(), 0, bytesRead)
                        }
                        buffer.clear()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // Preserve the interrupted status
                Log.d(TAG, "VPN thread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "VPN error: ", e)
            } finally {
                stopSelf()
            }
        }
        vpnThread?.start()
    }
    
    private fun isInLearningMode(): Boolean {
        val startTime = prefs.getLong("learning_mode_start_timestamp", 0L)
        if (startTime == 0L) return true // Should not happen, but as a fallback
        // 7 days in milliseconds
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - startTime) < sevenDaysInMillis
    }

    private fun shouldBlockPacket(packet: ByteBuffer): Boolean {
        try {
            val ipVersion = packet.get(0).toInt() ushr 4
            if (ipVersion != 4) return false

            val headerLength = (packet.get(0).toInt() and 0x0F) * 4
            val protocol = packet.get(9).toInt()
            
            if (protocol == 17) { // UDP
                val destPort = ((packet.get(headerLength + 2).toInt() and 0xFF) shl 8) or (packet.get(headerLength + 3).toInt() and 0xFF)
                if (destPort == 53) { // DNS
                    val domain = extractDomainFromDns(packet, headerLength + 8)
                    if (domain != null && DomainBlocklist.isBlocked(domain)) {
                        Log.w(TAG, "BLOCKED DNS: $domain")
                        showBlockingNotification(domain)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking packet block: ${e.message}")
        }
        return false
    }

    private fun extractDomainFromDns(packet: ByteBuffer, offset: Int): String? {
        try {
            var currentOffset = offset + 12
            val sb = StringBuilder()
            while (currentOffset < packet.limit()) {
                val length = packet.get(currentOffset).toInt() and 0xFF
                if (length == 0) break
                if (sb.isNotEmpty()) sb.append(".")
                currentOffset++
                for (i in 0 until length) {
                    sb.append(packet.get(currentOffset++).toInt().toChar())
                }
            }
            return sb.toString()
        } catch (e: Exception) {
            return null
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun showBlockingNotification(domain: String) {
        // Implementation omitted for brevity
    }

    private fun parseAndProcessPacket(packet: ByteBuffer) {
        try {
            val ipVersion = packet.get(0).toInt() ushr 4
            if (ipVersion != 4) return
            
            val protocol = packet.get(9).toInt()
            if (protocol != 6 && protocol != 17) return

            val ownerPackage = resolveLikelyActiveApp() ?: return
            if (ownerPackage == packageName) return

            val destIp = "${packet.get(16).toUByte()}.${packet.get(17).toUByte()}.${packet.get(18).toUByte()}.${packet.get(19).toUByte()}"
            
            if (isInLearningMode()) {
                buildProfile(ownerPackage, destIp)
            } else {
                checkNetworkAnomaly(ownerPackage, destIp)
            }
        } catch (e: Exception) { /* Packet parsing error */ }
    }
    
    private fun buildProfile(ownerPackage: String, destIp: String) {
        val profileKey = "profile_app_${ownerPackage}_ips"
        val trustedIps = prefs.getStringSet(profileKey, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (trustedIps.add(destIp)) { // Add returns true if the set was modified
            prefs.edit().putStringSet(profileKey, trustedIps).apply()
            Log.d(TAG, "Learning: Added $destIp to profile for $ownerPackage")
        }
    }

    private fun checkNetworkAnomaly(ownerPackage: String, destIp: String) {
        val trustedIps = prefs.getStringSet("profile_app_${ownerPackage}_ips", null)

        if (trustedIps == null || !trustedIps.contains(destIp)) {
            val description = "$ownerPackage ilovasi o\'zi uchun notanish $destIp manziliga ulandi."
            val exceptionKey = "exception_" + description.replace(" ", "_").take(50).replace(Regex("[^a-zA-Z0-9_]"), "")

            if (!prefs.getBoolean(exceptionKey, false)) {
                val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY_NETWORK\", \"description\":\"$description\", \"app\":\"$ownerPackage\", \"dest_ip\":\"$destIp\"}"
                sendActiveDefenseNotification(description, ownerPackage, anomalyJson)
            }
        }
    }

    private fun playAlertSound() {
        // Implementation omitted
    }
    
    @SuppressLint("MissingPermission")
    private fun sendActiveDefenseNotification(details: String, packageName: String, jsonLog: String) {
        // Implementation omitted
    }

    private fun resolveLikelyActiveApp(): String? {
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            return null
        }
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            stats?.filter { it.lastTimeUsed > now - 60_000 }?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.w(TAG, "resolveLikelyActiveApp failed: ${e.message}")
            null
        }
    }

    private fun writeToFile(text: String) {
        try {
            encryptedLogger.appendLog(LOG_FILE_NAME, "$text\n")
        } catch (e: Exception) {
            Log.e(TAG, "File write error: ${e.message}")
        }
    }

    private fun stopVpn() { 
        isRunning = false
        vpnThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: Exception) { }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() { 
        super.onDestroy()
        voiceAssistant.shutdown()
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }

    private fun createNotificationChannel() { 
       // Implementation omitted
    }

    private fun createForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.example.cyberapp.CONNECT"
        const val ACTION_DISCONNECT = "com.example.cyberapp.DISCONNECT"
        private const val FOREGROUND_NOTIFICATION_ID = 2
        private const val FOREGROUND_CHANNEL_ID = "CyberAppVpnChannel"
        var isRunning = false
    }
}
