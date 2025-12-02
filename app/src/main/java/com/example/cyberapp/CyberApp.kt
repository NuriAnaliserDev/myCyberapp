package com.example.cyberapp

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CyberApp : Application() {

    private lateinit var encryptedLogger: EncryptedLogger

    override fun onCreate() {
        super.onCreate()
        encryptedLogger = EncryptedLogger(this)
        encryptedLogger.migratePlainTextLog("crash_logs.txt")
        setupGlobalCrashHandler()
        
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }

    inner class AppLifecycleObserver : androidx.lifecycle.DefaultLifecycleObserver {
        private var backgroundTime: Long = 0

        override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
            backgroundTime = System.currentTimeMillis()
        }

        override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
            if (backgroundTime > 0 && System.currentTimeMillis() - backgroundTime > 60000) { // 1 minute lock timeout
                val pinManager = PinManager(this@CyberApp)
                if (pinManager.isPinSet()) {
                    val intent = Intent(this@CyberApp, PinActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
            backgroundTime = 0
        }
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logCrash(throwable)
                
                // Show toast on UI thread
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "CyberApp kutilmagan xatolik tufayli to'xtadi. Log saqlandi.", Toast.LENGTH_LONG).show()
                }
                
                // Wait a bit for Toast to show
                Thread.sleep(2000)
            } catch (e: Exception) {
                Log.e("CyberApp", "Crash handler failed: ${e.message}")
            } finally {
                // Pass to default handler or exit
                defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
            }
        }
    }

    private fun logCrash(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        
        val crashLog = "CRASH TIMESTAMP: ${System.currentTimeMillis()}\n$stackTrace\n\n"
        
        try {
            encryptedLogger.appendLog("crash_logs.txt", crashLog)
            Log.e("CyberApp", "CRASH SAVED: $stackTrace")
        } catch (e: Exception) {
            Log.e("CyberApp", "Failed to write crash log: ${e.message}")
        }
    }
}
