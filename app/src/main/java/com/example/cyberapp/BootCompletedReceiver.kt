package com.example.cyberapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Telefon o'chib-yonganda LoggerService'ni ishga tushiramiz
            val serviceIntent = Intent(context, LoggerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
