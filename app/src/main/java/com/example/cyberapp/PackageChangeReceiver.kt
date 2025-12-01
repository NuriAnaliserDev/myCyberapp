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
        val result = PhishingDetector.analyzePackage(context, packageName)

        if (result.isSuspicious) {
            showSuspiciousAppAlert(context, packageName, result)
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
