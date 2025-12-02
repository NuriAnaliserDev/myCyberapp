package com.example.cyberapp

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object PhishingDetector {

    // Xavfli ruxsatnomalar va ularning "bahosi"
    private val PERMISSION_RISK_SCORES = mapOf(
        android.Manifest.permission.READ_SMS to 20,
        android.Manifest.permission.RECEIVE_SMS to 25,
        android.Manifest.permission.SEND_SMS to 30,
        android.Manifest.permission.READ_CONTACTS to 15,
        android.Manifest.permission.WRITE_CONTACTS to 15,
        android.Manifest.permission.READ_CALL_LOG to 15,
        android.Manifest.permission.CAMERA to 10,
        android.Manifest.permission.RECORD_AUDIO to 20,
        android.Manifest.permission.ACCESS_FINE_LOCATION to 10,
        android.Manifest.permission.BIND_DEVICE_ADMIN to 100, // Eng xavfli
        android.Manifest.permission.SYSTEM_ALERT_WINDOW to 25
    )

    data class AnalysisResult(
        val isSuspicious: Boolean,
        val riskScore: Int, // 0-100+
        val warnings: List<String>
    )

    fun analyzePackage(context: Context, packageName: String): AnalysisResult {
        val pm = context.packageManager
        val warnings = mutableListOf<String>()
        var riskScore = 0

        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS)
            val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()

            // 1. Xavfli ruxsatnomalarni tahlil qilish
            for (permission in requestedPermissions) {
                val score = PERMISSION_RISK_SCORES[permission]
                if (score != null) {
                    riskScore += score
                    warnings.add("Xavfli ruxsatnoma: $permission ($score ball)")
                }
            }

            // 2. Maxsus josuslik belgilarini (pattern) tahlil qilish
            val hasSmsPermission = requestedPermissions.any { it.contains("SMS") }
            val hasContactsPermission = requestedPermissions.any { it.contains("CONTACTS") }
            val hasLocationPermission = requestedPermissions.any { it.contains("LOCATION") }

            if (hasSmsPermission && hasContactsPermission) {
                riskScore += 30 // Bonus ball
                warnings.add("DIQQAT: SMS va Kontaktlarga birga ruxsat so'ralgan - bu josuslik belgisi bo'lishi mumkin!")
            }
            
            if (hasSmsPermission && hasLocationPermission) {
                riskScore += 20 // Bonus ball
                warnings.add("DIQQAT: SMS va Joylashuvga birga ruxsat so'ralgan - bu moliyaviy firibgarlik belgisi bo'lishi mumkin!")
            }

            // 3. Ilovaning o'zini himoyasini tekshirish (agar bo'lsa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (packageInfo.applicationInfo.flags and 0x8000000 == 0) {
                     riskScore += 10
                     warnings.add("OGOHLANTIRISH: Ilova shifrlanmagan tarmoq trafigiga ruxsat beradi (cleartext traffic).")
                }
            }

            // Maksimal balldan oshib ketmaslik (bu yerda 100 dan oshishi ham mumkin, bu reyting uchun yaxshi)
            // if (riskScore > 100) riskScore = 100 

            return AnalysisResult(
                isSuspicious = riskScore >= 50, // Shubhali deb topish chegarasi
                riskScore = riskScore,
                warnings = warnings.sortedDescending() // Eng muhim ogohlantirishlar tepada
            )

        } catch (e: PackageManager.NameNotFoundException) {
            return AnalysisResult(false, 0, listOf("Ilova topilmadi"))
        } catch (e: Exception) {
            return AnalysisResult(false, 0, listOf("Tahlil xatosi: ${e.message}"))
        }
    }
}
