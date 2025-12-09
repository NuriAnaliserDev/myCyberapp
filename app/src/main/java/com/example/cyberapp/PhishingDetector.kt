package com.example.cyberapp

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object PhishingDetector {

    // Xavfsiz ilovalar whitelist (bu ilovalar hech qachon xavfli deb topilmaydi)
    private val TRUSTED_PACKAGES = setOf(
        // Messaging apps
        "org.telegram.messenger", // Telegram
        "com.whatsapp", // WhatsApp
        "com.viber.voip", // Viber
        
        // Video platforms
        "com.google.android.youtube", // YouTube
        "com.google.android.apps.youtube.music", // YouTube Music
        
        // Microsoft apps
        "com.microsoft.office.outlook", // Outlook
        "com.microsoft.office.officehubrow", // Microsoft Office
        "com.microsoft.teams", // Microsoft Teams
        "com.microsoft.skydrive", // OneDrive
        "com.microsoft.windowsintune.companyportal", // Intune
        
        // Payment apps (Uzbekistan)
        "com.payme.app", // PayMe
        "com.click.mobile", // Click
        "com.xazna.mobile", // Xazna
        "com.uzum.uzum", // Uzum Pay
        "com.uzcard.uzcard", // UzCard
        
        // Banking apps
        "com.humo.mobile", // Humo
        "com.orient.finance", // Orient Finance
        
        // Social media
        "com.instagram.android", // Instagram
        "com.facebook.katana", // Facebook
        "com.twitter.android", // Twitter
        "com.linkedin.android", // LinkedIn
        
        // Google apps
        "com.google.android.gm", // Gmail
        "com.google.android.apps.photos", // Google Photos
        "com.google.android.apps.docs", // Google Docs
        "com.google.android.apps.drive", // Google Drive
        "com.google.android.apps.maps", // Google Maps
        "com.android.chrome" // Chrome
    )

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
        // 0. Whitelist tekshiruvi - xavfsiz ilovalar hech qachon xavfli deb topilmaydi
        if (TRUSTED_PACKAGES.contains(packageName)) {
            return AnalysisResult(
                isSuspicious = false,
                riskScore = 0,
                warnings = emptyList()
            )
        }
        
        val pm = context.packageManager
        val warnings = mutableListOf<String>()
        var riskScore = 0

        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS)
            val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
            
            // System ilovalarni ham tekshirishdan o'tkazib yuborish (ular xavfsiz)
            if ((packageInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                return AnalysisResult(
                    isSuspicious = false,
                    riskScore = 0,
                    warnings = emptyList()
                )
            }

            // 1. Xavfli ruxsatnomalarni tahlil qilish
            for (permission in requestedPermissions) {
                val score = PERMISSION_RISK_SCORES[permission]
                if (score != null) {
                    riskScore += score
                    warnings.add("Xavfli ruxsatnoma: $permission ($score ball)")
                }
            }

            // 2. Maxsus josuslik belgilarini (pattern) tahlil qilish
            // Faqat juda xavfli kombinatsiyalar uchun bonus ball
            val hasSmsPermission = requestedPermissions.any { it.contains("SMS") }
            val hasContactsPermission = requestedPermissions.any { it.contains("CONTACTS") }
            val hasLocationPermission = requestedPermissions.any { it.contains("LOCATION") }
            val hasDeviceAdmin = requestedPermissions.any { it.contains("DEVICE_ADMIN") }

            // Faqat juda xavfli kombinatsiyalar uchun bonus
            if (hasSmsPermission && hasContactsPermission && hasDeviceAdmin) {
                riskScore += 50 // Eng xavfli kombinatsiya
                warnings.add("DIQQAT: SMS, Kontaktlar va Device Admin birga - bu josuslik ilovasi bo'lishi mumkin!")
            } else if (hasSmsPermission && hasContactsPermission) {
                riskScore += 20 // Kamaytirildi
                warnings.add("OGOHLANTIRISH: SMS va Kontaktlarga birga ruxsat so'ralgan")
            }
            
            if (hasSmsPermission && hasLocationPermission && hasDeviceAdmin) {
                riskScore += 40 // Xavfli kombinatsiya
                warnings.add("DIQQAT: SMS, Joylashuv va Device Admin birga - bu moliyaviy firibgarlik ilovasi bo'lishi mumkin!")
            } else if (hasSmsPermission && hasLocationPermission) {
                riskScore += 15 // Kamaytirildi
                warnings.add("OGOHLANTIRISH: SMS va Joylashuvga birga ruxsat so'ralgan")
            }

            // 3. Ilovaning o'zini himoyasini tekshirish (agar bo'lsa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (packageInfo.applicationInfo.flags and 0x8000000 == 0) {
                     riskScore += 5 // Kamaytirildi
                     warnings.add("OGOHLANTIRISH: Ilova shifrlanmagan tarmoq trafigiga ruxsat beradi (cleartext traffic).")
                }
            }

            // Risk score threshold ni oshirish - faqat juda xavfli ilovalar shubhali deb topiladi
            return AnalysisResult(
                isSuspicious = riskScore >= 70, // Oshirildi 50 dan 70 ga
                riskScore = riskScore,
                warnings = warnings.sortedDescending() // Eng muhim ogohlantirishlar tepada
            )

        } catch (e: PackageManager.NameNotFoundException) {
            return AnalysisResult(false, 0, listOf("Ilova topilmadi"))
        } catch (e: Exception) {
            return AnalysisResult(false, 0, listOf("Tahlil xatosi: ${e.message}"))
        }
    }
    data class UrlAnalysisResult(
        val isSuspicious: Boolean,
        val riskScore: Int,
        val warnings: List<String>
    )

    fun analyzeUrl(url: String): UrlAnalysisResult {
        val warnings = mutableListOf<String>()
        var riskScore = 0

        // 1. Homograph Attack Detection (Mixed Scripts)
        if (isHomographAttack(url)) {
            riskScore += 50
            warnings.add("DIQQAT: Homograph hujum aniqlandi! (Kirill va Lotin harflari aralash)")
        }

        // 2. Evilginx2 / MITM Patterns
        if (detectEvilginxPatterns(url)) {
            riskScore += 60
            warnings.add("DIQQAT: Evilginx2 yoki MITM hujum belgilari aniqlandi!")
        }

        // 3. Suspicious Keywords
        val suspiciousKeywords = listOf(
            "verify", "secure", "login", "account", "update", "banking",
            "paypal", "confirm", "suspended", "locked", "unusual", "signin", "auth"
        )
        val lowerUrl = url.lowercase()
        var keywordCount = 0
        for (keyword in suspiciousKeywords) {
            if (lowerUrl.contains(keyword)) keywordCount++
        }
        
        // Lower threshold for keyword detection
        if (keywordCount >= 1 && riskScore < 30) {
            riskScore += 30
            warnings.add("URL manzilda shubhali so'zlar mavjud ($keywordCount ta)")
        } else if (keywordCount >= 2) {
             riskScore += 20 // Extra penalty for multiple keywords
        }

        return UrlAnalysisResult(
            isSuspicious = riskScore >= 30, // Lowered threshold from 50 to 30
            riskScore = riskScore,
            warnings = warnings
        )
    }

    private fun isHomographAttack(url: String): Boolean {
        var domain = ""
        try {
            domain = java.net.URI(url).host ?: ""
        } catch (e: Exception) {
            domain = ""
        }

        if (domain.isEmpty()) {
            // Fallback: extract domain using string manipulation
            val afterScheme = if (url.contains("://")) url.substringAfter("://") else url
            domain = afterScheme.substringBefore("/")
        }
        
        if (domain.isEmpty()) return false
        
        // Regex to detect mixed Cyrillic and Latin characters
        val hasLatin = domain.matches(".*[a-zA-Z].*".toRegex())
        val hasCyrillic = domain.matches(".*[\\u0400-\\u04FF].*".toRegex())

        return hasLatin && hasCyrillic
    }

    private fun detectEvilginxPatterns(url: String): Boolean {
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return false
            val path = uri.path ?: ""

            // Evilginx often uses very long subdomains or random path segments
            // Example: login.google.com.evil-site.com
            
            val parts = host.split(".")
            if (parts.size > 4) return true // Suspiciously deep subdomain

            // Check for common brands in subdomains but not in the main domain (simplified check)
            val brands = listOf("google", "facebook", "instagram", "paypal", "microsoft")
            val isBrandInHost = brands.any { host.contains(it) }
            
            // If brand is present, but it's not the official TLD (very basic check)
            if (isBrandInHost) {
                val isOfficial = brands.any { host.endsWith("$it.com") || host.endsWith("$it.uz") }
                if (!isOfficial) return true
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }
}
