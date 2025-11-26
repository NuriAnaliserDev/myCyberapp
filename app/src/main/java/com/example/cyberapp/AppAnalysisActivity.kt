package com.example.cyberapp

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class AppAnalysisActivity : AppCompatActivity() {

    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private val appList = mutableListOf<AppInfo>()

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

        setupRecyclerView()
        loadApps()
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
                    AppInfo(appName, packageName, icon, riskScore)
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
}
