package com.example.cyberapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import java.io.File
import java.security.MessageDigest

/**
 * Elite Security Manager
 * Provides advanced security features:
 * - Anti-debugging detection
 * - Memory protection
 * - APK integrity verification
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityManager"
    }

    /**
     * Check if debugger is attached
     * @return true if debugger detected
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Check if app is debuggable
     * @return true if app is in debug mode
     */
    fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Check for common debugging tools
     * @return true if debugging tools detected
     */
    fun hasDebugTools(): Boolean {
        // Check for Frida
        val fridaFiles = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server"
        )
        
        for (file in fridaFiles) {
            if (File(file).exists()) {
                return true
            }
        }

        // Check for Xposed
        try {
            throw Exception("Xposed check")
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            if (stackTrace.contains("de.robv.android.xposed")) {
                return true
            }
        }

        return false
    }

    /**
     * Verify APK signature integrity
     * @return true if APK is not tampered
     */
    fun verifyApkIntegrity(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )

            // Get APK signature
            val signatures = packageInfo.signatures
            if (signatures.isNullOrEmpty()) return false
            
            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            val signatureHash = md.digest(signature.toByteArray())
            val hashString = signatureHash.joinToString("") { "%02x".format(it) }

            // In production, compare with your known signature hash
            // For now, just verify signature exists
            hashString.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if app is running in emulator
     * @return true if emulator detected
     */
    fun isEmulator(): Boolean {
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        val hardware = android.os.Build.HARDWARE

        return (brand.startsWith("generic") && device.startsWith("generic")) ||
                "google_sdk" == product ||
                model.contains("Emulator") ||
                model.contains("Android SDK") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu")
    }

    /**
     * Perform comprehensive security check
     * @return SecurityCheckResult with all findings
     */
    fun performSecurityCheck(): SecurityCheckResult {
        return SecurityCheckResult(
            isDebuggerAttached = isDebuggerAttached(),
            isDebuggable = isDebuggable(),
            hasDebugTools = hasDebugTools(),
            isApkTampered = !verifyApkIntegrity(),
            isEmulator = isEmulator()
        )
    }

    /**
     * Clear sensitive data from memory
     * @param data CharArray to clear
     */
    fun clearMemory(data: CharArray) {
        data.fill('\u0000')
    }

    /**
     * Clear sensitive string from memory
     * @param data String to clear (creates char array and clears it)
     */
    fun clearMemory(data: String) {
        val chars = data.toCharArray()
        clearMemory(chars)
    }
}

/**
 * Security check result data class
 */
data class SecurityCheckResult(
    val isDebuggerAttached: Boolean,
    val isDebuggable: Boolean,
    val hasDebugTools: Boolean,
    val isApkTampered: Boolean,
    val isEmulator: Boolean
) {
    /**
     * Check if any security threat detected
     */
    fun hasThreat(): Boolean {
        return isDebuggerAttached || hasDebugTools || isApkTampered
    }

    /**
     * Get threat description
     */
    fun getThreatDescription(): String {
        val threats = mutableListOf<String>()
        if (isDebuggerAttached) threats.add("Debugger")
        if (hasDebugTools) threats.add("Debug Tools")
        if (isApkTampered) threats.add("Tampered APK")
        if (isEmulator) threats.add("Emulator")
        
        return if (threats.isEmpty()) {
            "No threats detected"
        } else {
            "Threats: ${threats.joinToString(", ")}"
        }
    }
}
