package com.example.cyberapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = EncryptedPrefsManager(context)
            val isProtectionEnabled = prefs.getBoolean("protection_enabled", false)

            if (isProtectionEnabled && PermissionHelper.hasUsageStatsPermission(context)) {
                val serviceIntent = Intent(context, LoggerService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                Log.w(TAG, context.getString(R.string.usage_stats_permission_not_granted))
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
