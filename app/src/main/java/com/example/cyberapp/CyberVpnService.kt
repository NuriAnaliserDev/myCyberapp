package com.example.cyberapp

import android.Manifest
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
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

    override fun onCreate() {
        super.onCreate()
        prefs = EncryptedPrefsManager(this)
        encryptedLogger = EncryptedLogger(this)
        encryptedLogger.migratePlainTextLog(LOG_FILE_NAME)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) startVpn()
        if (intent?.action == ACTION_DISCONNECT) stopVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnThread?.isAlive == true) return

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }

        vpnThread = Thread {
            try {
                vpnInterface = Builder()
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("8.8.8.8")
                    // .addRoute("0.0.0.0", 0) // COMMENTED OUT: Fixes internet blocking. Now runs in "Passive Mode".
                    .setSession(getString(R.string.app_name))
                    .establish()

                val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
                val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)

                while (!Thread.interrupted()) {
                    val bytesRead = vpnInput.read(buffer.array())
                    if (bytesRead > 0) {
                        buffer.limit(bytesRead)
                        
                        // Check for blocking (DNS Filter)
                        if (shouldBlockPacket(buffer)) {
                            buffer.clear()
                            continue
                        }

                        parseAndProcessPacket(buffer.duplicate()) // Send copy to preserve original
                        
                        vpnOutput.write(buffer.array(), 0, bytesRead)
                        buffer.clear()
                    }
                }
            } catch (e: Exception) {
                if (e !is InterruptedException) Log.e(TAG, "VPN error: ", e)
            } finally {
                stopSelf()
            }
        }
        vpnThread?.start()
    }

    private fun shouldBlockPacket(packet: ByteBuffer): Boolean {
        try {
            val ipVersion = packet.get(0).toInt() ushr 4
            if (ipVersion != 4) return false

            val headerLength = (packet.get(0).toInt() and 0x0F) * 4
            val protocol = packet.get(9).toInt()
            
            // UDP = 17
            if (protocol == 17) {
                val destPort = ((packet.get(headerLength + 2).toInt() and 0xFF) shl 8) or (packet.get(headerLength + 3).toInt() and 0xFF)
                
                // DNS = 53
                if (destPort == 53) {
                    val dnsHeaderOffset = headerLength + 8 // IP Header + UDP Header (8 bytes)
                    val domain = extractDomainFromDns(packet, dnsHeaderOffset)
                    
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
            // DNS Header is 12 bytes. Question starts at offset + 12
            var currentOffset = offset + 12
            val sb = StringBuilder()
            
            while (currentOffset < packet.limit()) {
                val length = packet.get(currentOffset).toInt() and 0xFF
                if (length == 0) break // End of name
                
                if (sb.isNotEmpty()) sb.append(".")
                
                currentOffset++
                for (i in 0 until length) {
                    sb.append(packet.get(currentOffset).toInt().toChar())
                    currentOffset++
                }
            }
            return sb.toString()
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun showBlockingNotification(domain: String) {
        val notification = NotificationCompat.Builder(this, ANOMALY_CHANNEL_ID)
            .setContentTitle("🚫 Xavfli Sayt Bloklandi")
            .setContentText(domain)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(android.graphics.Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        NotificationManagerCompat.from(this).notify(domain.hashCode(), notification)
    }

    private fun parseAndProcessPacket(packet: ByteBuffer) {
        try {
            val ipVersion = packet.get(0).toInt() ushr 4
            if (ipVersion != 4) return
            
            // ... (rest of existing logic)

            val protocol = packet.get(9).toInt()
            if (protocol != 6 && protocol != 17) return

            val ownerPackage = resolveLikelyActiveApp() ?: return
            if (ownerPackage == packageName) return

            val destIp = "${packet.get(16).toUByte()}.${packet.get(17).toUByte()}.${packet.get(18).toUByte()}.${packet.get(19).toUByte()}"
            
            val isProfileCreated = prefs.getBoolean("isProfileCreated", false)
            if (!isProfileCreated) {
                val dataJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"DATA_NETWORK\", \"app\":\"$ownerPackage\", \"dest_ip\":\"$destIp\"}"
                writeToFile(dataJson)
            } else {
                checkNetworkAnomaly(ownerPackage, destIp)
            }
        } catch (e: Exception) { /* Packet parsing error */ }
    }

    private fun checkNetworkAnomaly(ownerPackage: String, destIp: String) {
        val trustedIps = prefs.getStringSet("profile_app_${ownerPackage}_ips", null)

        // If no profile for app or IP not in whitelist - anomaly
        if (trustedIps == null || !trustedIps.contains(destIp)) {
            val description = "$ownerPackage ilovasi o'zi uchun notanish $destIp manziliga ulandi."
            val exceptionKey = "exception_" + description.replace(" ", "_").take(50)

            if (!prefs.getBoolean(exceptionKey, false)) {
                val anomalyJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"ANOMALY_NETWORK\", \"description\":\"$description\", \"app\":\"$ownerPackage\"}"
                sendActiveDefenseNotification(description, ownerPackage, anomalyJson)
            }
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
    
    @android.annotation.SuppressLint("MissingPermission")
    private fun sendActiveDefenseNotification(details: String, packageName: String, jsonLog: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 Log.w(TAG, "Notification permission missing, skipping alert")
                 return
             }
        }
        writeToFile(jsonLog)
        
        // Play signature alert sound
        playAlertSound()

        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        val uninstallPendingIntent = PendingIntent.getActivity(this, packageName.hashCode(), uninstallIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val uninstallAction = NotificationCompat.Action.Builder(0, "O'CHIRISH", uninstallPendingIntent).build()
        
        val detailsIntent = Intent(this, MainActivity::class.java)
        val detailsPendingIntent = PendingIntent.getActivity(this, 1, detailsIntent, PendingIntent.FLAG_IMMUTABLE)
        val detailsAction = NotificationCompat.Action.Builder(0, "Tafsilotlar", detailsPendingIntent).build()
        
        val notification = NotificationCompat.Builder(this, ANOMALY_CHANNEL_ID)
            .setContentTitle("⚠️ TARMOQ XAVFI ANIQLANDI!")
            .setContentText(details)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(getColor(R.color.cyber_alert))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .addAction(uninstallAction)
            .addAction(detailsAction)
            .setAutoCancel(true)
            .build()
            
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
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
        vpnThread?.interrupt()
        vpnInterface?.close() 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() { 
        super.onDestroy()
        stopVpn()
    }

    private fun createNotificationChannel() { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { 
            val aChannel = NotificationChannel(ANOMALY_CHANNEL_ID, "Anomaly Alerts", NotificationManager.IMPORTANCE_HIGH)
            val fChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(aChannel)
            nm.createNotificationChannel(fChannel)
        } 
    }

    private fun createForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("CyberApp VPN")
            .setContentText("Tarmoq himoyasi faol")
            .setSmallIcon(R.drawable.ic_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.example.cyberapp.CONNECT"
        const val ACTION_DISCONNECT = "com.example.cyberapp.DISCONNECT"
        private const val FOREGROUND_NOTIFICATION_ID = 2
        private const val FOREGROUND_CHANNEL_ID = "CyberAppVpnChannel"
    }
}
