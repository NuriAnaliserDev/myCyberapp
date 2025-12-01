package com.example.cyberapp

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object PhishingDetector {

    private val DANGEROUS_PERMISSIONS = listOf(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    data class AnalysisResult(
        val isSuspicious: Boolean,
        val riskScore: Int, // 0-100
        val warnings: List<String>
    )

    fun analyzePackage(context: Context, packageName: String): AnalysisResult {
        val pm = context.packageManager
        val warnings = mutableListOf<String>()
        var riskScore = 0

        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val requestedPermissions = packageInfo.requestedPermissions ?: return AnalysisResult(false, 0, emptyList())

            var hasSms = false
            var hasContacts = false
            for (permission in requestedPermissions) {
                if (permission.contains("SMS")) hasSms = true
                if (permission.contains("CONTACTS")) hasContacts = true
            }

            // Heuristic: SMS + Contacts is a common loan shark / spyware pattern
            if (hasSms && hasContacts) {
                riskScore += 30
                warnings.add("DIQQAT: SMS va Kontaktlarni birga o'qish - bu josuslik belgisi bo'lishi mumkin!")
            }

            // Cap score
            if (riskScore > 100) riskScore = 100

            return AnalysisResult(
                isSuspicious = riskScore >= 40,
                riskScore = riskScore,
                warnings = warnings
            )

        } catch (e: PackageManager.NameNotFoundException) {
            return AnalysisResult(false, 0, listOf("Ilova topilmadi"))
        } catch (e: Exception) {
            return AnalysisResult(false, 0, listOf("Tahlil xatosi: ${e.message}"))
        }
    }
}
