package com.example.cyberapp

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Button
import android.widget.Toast
import java.io.FileInputStream
import java.security.MessageDigest

class AppAnalysisActivity : AppCompatActivity() {

    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private val appList = mutableListOf<AppInfo>()
    private lateinit var prefs: EncryptedPrefsManager

    // Xavfli ruxsatnomalar va ularning "bahosi"
    private val permissionRiskScores = mapOf(
        "android.permission.SEND_SMS" to 20,
        "android.permission.READ_CONTACTS" to 15,
        "android.permission.WRITE_CONTACTS" to 15,
        "android.permission.CAMERA" to 10,
        "android.permission.RECORD_AUDIO" to 15,
        "android.permission.ACCESS_FINE_LOCATION" to 10,
        "android.permission.READ_CALL_LOG" to 15,
        "android.permission.BIND_DEVICE_ADMIN" to 100, // Eng xavfli
        "android.permission.SYSTEM_ALERT_WINDOW" to 25
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_analysis)

        prefs = EncryptedPrefsManager(this)
        maybeShowVisibilityNotice()

        setupRecyclerView()

        
        findViewById<Button>(R.id.btn_scan_virustotal).setOnClickListener {
            scanApps()
        }

        loadApps()
    }

    private fun maybeShowVisibilityNotice() {
        val key = "app_analysis_visibility_notice"
        if (!prefs.getBoolean(key, false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.app_analysis_visibility_title)
                .setMessage(R.string.app_analysis_visibility_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            prefs.putBoolean(key, true)
        }
    }

    private fun setupRecyclerView() {
        appsRecyclerView = findViewById(R.id.apps_recyclerview)
        appAdapter = AppAdapter(appList)
        appsRecyclerView.adapter = appAdapter
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadApps() {
        // Ilovalarni fon oqimida yuklash (interfeys qotmasligi uchun)
        thread {
            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            val installedApps = packages.mapNotNull { packageInfo ->
                // Xavfsiz blok: Agar applicationInfo null bo'lsa, bu ilovani o'tkazib yuboradi
                packageInfo.applicationInfo?.let { appInfo ->
                    val appName = appInfo.loadLabel(pm).toString()
                    val packageName = packageInfo.packageName
                    val icon = appInfo.loadIcon(pm)
                    val riskScore = calculateRiskScore(packageInfo)
                    val sourceDir = appInfo.sourceDir
                    AppInfo(appName, packageName, icon, riskScore, sourceDir)
                }
            }
            
            // Eng xavflilarini tepaga chiqarib saralash
            val sortedApps = installedApps.sortedByDescending { it.riskScore }

            runOnUiThread {
                appList.clear()
                appList.addAll(sortedApps)
                appAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun calculateRiskScore(packageInfo: PackageInfo): Int {
        var score = 0
        val requestedPermissions = packageInfo.requestedPermissions
        if (requestedPermissions != null) {
            for (permission in requestedPermissions) {
                score += permissionRiskScores.getOrDefault(permission, 0)
            }
        }
        return score
    }

    private fun scanApps() {
        val btnScan = findViewById<Button>(R.id.btn_scan_virustotal)
        btnScan.isEnabled = false
        btnScan.text = "Tekshirilmoqda..."
        Toast.makeText(this, "Tekshirish boshlandi. Bu biroz vaqt olishi mumkin...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            for ((index, app) in appList.withIndex()) {
                try {
                    // UI da "Tekshirilmoqda" deb ko'rsatish
                    withContext(Dispatchers.Main) {
                        app.virusTotalStatus = "Tekshirilmoqda..."
                        appAdapter.notifyItemChanged(index)
                    }

                    val hash = calculateSHA256(app.sourceDir)
                    val response = com.example.cyberapp.network.RetrofitClient.api.checkApk(
                        com.example.cyberapp.network.ApkCheckRequest(hash)
                    )
                    
                    withContext(Dispatchers.Main) {
                        app.virusTotalStatus = if (response.verdict == "malicious") "Xavfli! (${response.score})" else "Toza"
                        appAdapter.notifyItemChanged(index)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        app.virusTotalStatus = "Xato" // Tarmoq xatosi yoki boshqa
                        appAdapter.notifyItemChanged(index)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                btnScan.isEnabled = true
                btnScan.text = "Qayta tekshirish"
                Toast.makeText(this@AppAnalysisActivity, "Tekshirish yakunlandi!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateSHA256(filePath: String): String {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("SHA-256")
        val fis = FileInputStream(filePath)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        fis.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
