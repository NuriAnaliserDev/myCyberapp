package com.example.cyberapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PermissionHelper.hasUsageStatsPermission(context)) {
                val serviceIntent = Intent(context, LoggerService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                Log.w(TAG, "Usage stats ruxsati yo'q – Boot paytida LoggerService ishga tushmadi")
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
