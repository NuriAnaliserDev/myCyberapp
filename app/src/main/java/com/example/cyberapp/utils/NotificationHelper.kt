package com.example.cyberapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.cyberapp.R

object NotificationHelper {

    const val CHANNEL_ID_STATUS = "CyberAppStatus"
    const val CHANNEL_ID_SCAN = "CyberAppScan"
    const val CHANNEL_ID_AUTH = "CyberAppAuth"
    const val NOTIFICATION_ID_STATUS = 1001
    const val NOTIFICATION_ID_SCAN = 1002
    const val NOTIFICATION_ID_AUTH = 1003

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                "Active Protection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that NuriSafety is active"
            }

            val scanChannel = NotificationChannel(
                CHANNEL_ID_SCAN,
                "Scan Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for URL and App scanning"
                enableVibration(true)
            }

            val authChannel = NotificationChannel(
                CHANNEL_ID_AUTH,
                "Authentication Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for authentication and token expiration"
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(scanChannel)
            manager.createNotificationChannel(authChannel)
        }
    }

    fun showStatusNotification(context: Context, content: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(R.drawable.ic_logo) // Ensure this exists
            .setContentTitle("NuriSafety: Faol")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_STATUS, notification)
    }

    fun showScanningNotification(context: Context, url: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCAN)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("Tekshirilmoqda...")
            .setContentText(url)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SCAN, notification)
    }

    fun showSafeNotification(context: Context, url: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCAN)
            .setSmallIcon(R.drawable.ic_logo) // Ideally a checkmark icon
            .setContentTitle("✅ Xavfsiz")
            .setContentText("Havola xavfsiz: $url")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(3000) // Disappear after 3s
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SCAN, notification)
    }

    fun showDangerNotification(context: Context, url: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCAN)
            .setSmallIcon(R.drawable.ic_logo) // Ideally a warning icon
            .setContentTitle("🚨 XAVF ANIQLANDI!")
            .setContentText("Havola bloklandi: $url")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SCAN, notification)
    }

    fun showTokenExpiringSoonNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_AUTH)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(context.getString(R.string.token_expiring_soon_title))
            .setContentText(context.getString(R.string.token_expiring_soon_message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_AUTH, notification)
    }

    fun showTokenExpiredNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_AUTH)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(context.getString(R.string.token_expired_title))
            .setContentText(context.getString(R.string.token_expired_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_AUTH, notification)
    }

    fun showAuthenticationErrorNotification(context: Context, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_AUTH)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(context.getString(R.string.authentication_error_title))
            .setContentText(context.getString(R.string.authentication_error_message, error))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_AUTH, notification)
    }
}
