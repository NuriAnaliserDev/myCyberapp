package com.example.cyberapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.security.MessageDigest
import com.example.cyberapp.network.RetrofitClient
import com.example.cyberapp.network.ApkCheckRequest

class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "CyberAppInstallMonitor"
        const val TAG = "PackageChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            
            // O'zimizni tekshirmaymiz
            if (packageName == context.packageName) return

            Log.d(TAG, "New package installed: $packageName")
            analyzeNewPackage(context, packageName)
        }
    }

    private fun analyzeNewPackage(context: Context, packageName: String) {
        // 1. Local Permission Analysis
        val result = PhishingDetector.analyzePackage(context, packageName)

        if (result.isSuspicious) {
            showSuspiciousAppAlert(context, packageName, result)
        }

        // 2. Cloud VirusTotal Scan
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val sourceDir = appInfo.sourceDir
                
                val hash = calculateSHA256(sourceDir)
                val apiResponse = RetrofitClient.api.checkApk(ApkCheckRequest(hash))

                if (apiResponse.verdict == "malicious") {
                    showVirusAlert(context, packageName, apiResponse.score)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Virus scan failed: ${e.message}")
            }
        }
    }

    private fun calculateSHA256(filePath: String): String {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("SHA-256")
        val fis = FileInputStream(filePath)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        fis.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun showVirusAlert(context: Context, packageName: String, score: Int) {
        createNotificationChannel(context)

        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        val uninstallPendingIntent = PendingIntent.getActivity(
            context, 
            packageName.hashCode(), 
            uninstallIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("🚨 VIRUS ANIQLANDI!")
            .setContentText("$packageName ilovasi VirusTotal bazasida xavfli deb topildi!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$packageName ilovasi xavfli! (Xavf darajasi: $score)\nIltimos, darhol o'chirib tashlang!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(android.graphics.Color.RED)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .addAction(android.R.drawable.ic_menu_delete, "O'CHIRISH", uninstallPendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(packageName.hashCode() + 1, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing")
        }
    }

    private fun showSuspiciousAppAlert(context: Context, packageName: String, result: PhishingDetector.AnalysisResult) {
        createNotificationChannel(context)

        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        val uninstallPendingIntent = PendingIntent.getActivity(
            context, 
            packageName.hashCode(), 
            uninstallIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val warningText = result.warnings.joinToString("\n") { "- $it" }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo) // Ensure this exists, fallback if needed
            .setContentTitle("⚠️ Xavfli Ilova Aniqlandi!")
            .setContentText("$packageName shubhali ruxsatlarga ega.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$packageName quyidagi xavfli ruxsatlarni so'ramoqda:\n$warningText\n\nBu firibgarlik ilovasi bo'lishi mumkin!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(android.graphics.Color.RED)
            .addAction(android.R.drawable.ic_menu_delete, "O'CHIRISH", uninstallPendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Install Monitor"
            val descriptionText = "Alerts for suspicious new apps"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
