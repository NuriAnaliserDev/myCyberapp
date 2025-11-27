package com.example.cyberapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class CyberVpnService : VpnService() {

    private val TAG = "CyberVpnService"
    private val LOG_FILE_NAME = "behaviour_logs.jsonl"
    private val ANOMALY_CHANNEL_ID = "CyberAppAnomalyChannel"
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var encryptedLogger: EncryptedLogger

    companion object {
        const val ACTION_CONNECT = "com.example.cyberapp.CONNECT"
        const val ACTION_DISCONNECT = "com.example.cyberapp.DISCONNECT"
    }

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
        vpnThread = Thread {
            try {
                // FIX: Changed route from "0.0.0.0", 0 (All Traffic) to "10.0.0.0", 24 (Local Only)
                // This prevents the VPN from blocking real internet traffic while still running the service.
                vpnInterface = Builder().addAddress("10.0.0.2", 24).addDnsServer("8.8.8.8").addRoute("10.0.0.0", 24).setSession(getString(R.string.app_name)).establish()
                val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
                val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)

                while (!Thread.interrupted()) {
                    val bytesRead = vpnInput.read(buffer.array())
                    if (bytesRead > 0) {
                        buffer.limit(bytesRead)
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

    private fun parseAndProcessPacket(packet: ByteBuffer) {
        try {
            val ipVersion = packet.get(0).toInt() ushr 4
            if (ipVersion != 4) return

            val protocol = packet.get(9).toInt()
            val protocolName = when(protocol) { 6 -> "tcp"; 17 -> "udp"; else -> return }

            val sourcePort = packet.getShort(20).toUShort().toInt()
            val ownerUid = findUidByPort(sourcePort, protocolName)
            if (ownerUid != -1) {
                val ownerPackage = packageManager.getNameForUid(ownerUid)
                if (ownerPackage != null && ownerPackage != packageName) {
                    val destIp = "${packet.get(16).toUByte()}.${packet.get(17).toUByte()}.${packet.get(18).toUByte()}.${packet.get(19).toUByte()}"
                    
                    val isProfileCreated = prefs.getBoolean("isProfileCreated", false)
                    if (!isProfileCreated) {
                        // LEARNING MODE
                        val dataJson = "{\"timestamp\":${System.currentTimeMillis()}, \"type\":\"DATA_NETWORK\", \"app\":\"$ownerPackage\", \"dest_ip\":\"$destIp\"}"
                        writeToFile(dataJson)
                    } else {
                        // ACTIVE DEFENSE MODE
                        checkNetworkAnomaly(ownerPackage, destIp)
                    }
                }
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
    
    private fun sendActiveDefenseNotification(details: String, packageName: String, jsonLog: String) { 
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

    private fun writeToFile(text: String) {
        try {
            encryptedLogger.appendLog(LOG_FILE_NAME, "$text\n")
        } catch (e: Exception) {
            Log.e(TAG, "File write error: ${e.message}")
        }
    }

    private fun findUidByPort(port: Int, protocol: String): Int { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { 
            try { 
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                if (protocol == "tcp") { 
                    return cm.getConnectionOwnerUid(6, InetSocketAddress(0), InetSocketAddress(port)) 
                } 
            } catch (e: SecurityException) { 
                Log.e(TAG, "UID topish uchun ruxsat yo'q.") 
            } 
        }
        var foundUid = -1
        try { 
            File("/proc/net/$protocol").bufferedReader().use { reader -> 
                reader.readLine()
                var line: String?
                while (true) { 
                    line = reader.readLine()
                    if (line == null) break
                    val parts = line!!.trim().split("\\s+".toRegex())
                    if (parts.size > 8) { 
                        try { 
                            val localPort = parts[1].split(":")[1].toInt(16)
                            if (port == localPort) { 
                                foundUid = parts[7].toInt()
                                break
                            } 
                        } catch (e: Exception) {} 
                    } 
                } 
            } 
        } catch (e: IOException) { 
            Log.e(TAG, "/proc fayllarini o'qishda xatolik: ${e.message}") 
        } 
        return foundUid
    }

    private fun stopVpn() { 
        vpnThread?.interrupt()
        vpnInterface?.close() 
    }

    override fun onDestroy() { 
        super.onDestroy()
        stopVpn()
    }

    private fun createNotificationChannel() { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { 
            val aChannel = NotificationChannel(ANOMALY_CHANNEL_ID, "Anomaly Alerts", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(aChannel)
        } 
    }
}
