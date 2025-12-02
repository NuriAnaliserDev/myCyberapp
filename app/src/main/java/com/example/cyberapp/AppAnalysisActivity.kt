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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_analysis)

        setupRecyclerView()
        loadAndAnalyzeApps()
    }

    private fun setupRecyclerView() {
        appsRecyclerView = findViewById(R.id.apps_recyclerview)
        appAdapter = AppAdapter(appList)
        appsRecyclerView.adapter = appAdapter
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadAndAnalyzeApps() {
        thread {
            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            val installedApps = packages.mapNotNull { packageInfo ->
                packageInfo.applicationInfo?.let {
                    val appName = it.loadLabel(pm).toString()
                    val packageName = packageInfo.packageName
                    val icon = it.loadIcon(pm)
                    
                    // Yangi PhishingDetector'dan foydalanish
                    val analysisResult = PhishingDetector.analyzePackage(this, packageName)
                    
                    val appInfo = AppInfo(appName, packageName, icon, analysisResult.riskScore)
                    appInfo.analysisWarnings = analysisResult.warnings // Ogohlantirishlarni qo'shish
                    appInfo
                }
            }
            
            val sortedApps = installedApps.sortedByDescending { it.riskScore }

            runOnUiThread {
                appList.clear()
                appList.addAll(sortedApps)
                appAdapter.notifyDataSetChanged()
            }
        }
    }
}
