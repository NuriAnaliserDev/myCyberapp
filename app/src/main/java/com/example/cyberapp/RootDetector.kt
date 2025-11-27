package com.example.cyberapp

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * Root Detection Utility
 * Detects if the device is rooted using multiple methods
 */
class RootDetector(private val context: Context) {

    /**
     * Check if device is rooted
     * @return true if device is rooted, false otherwise
     */
    fun isRooted(): Boolean {
        return checkRootFiles() || checkSuBinary() || checkRootApps()
    }

    /**
     * Check for common root files
     */
    private fun checkRootFiles(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu"
        )
        
        return rootPaths.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Check if su binary is accessible
     */
    private fun checkSuBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for root management apps
     */
    private fun checkRootApps(): Boolean {
        val rootPackages = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )

        return rootPackages.any { packageName ->
            isPackageInstalled(packageName)
        }
    }

    /**
     * Check if a package is installed
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Get detailed root detection info for logging
     */
    fun getRootDetectionDetails(): String {
        val details = StringBuilder()
        details.append("Root Detection Results:\n")
        details.append("- Root Files: ${checkRootFiles()}\n")
        details.append("- SU Binary: ${checkSuBinary()}\n")
        details.append("- Root Apps: ${checkRootApps()}\n")
        details.append("- Overall: ${isRooted()}")
        return details.toString()
    }
}
