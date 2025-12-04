package com.example.cyberapp

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.example.cyberapp.modules.apk_scanner.AppAdapter
import com.example.cyberapp.modules.apk_scanner.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class AppAnalysisActivity : AppCompatActivity() {

    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private val appList = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_analysis)

        val titleText = intent.getStringExtra("TITLE") ?: "App Analysis"
        findViewById<android.widget.TextView>(R.id.header_title).text = titleText

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            finish()
        }

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
        val shimmerViewContainer = findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val appListTemp = mutableListOf<AppInfo>()

            for (packageInfo in packages) {
                // Skip system apps if needed, but for security check we scan all
                packageInfo.applicationInfo?.let { appInfo ->
                    val appName = appInfo.loadLabel(pm).toString()
                    val packageName = packageInfo.packageName
                    val icon = appInfo.loadIcon(pm)
                    val sourceDir = appInfo.sourceDir
                    
                    // 1. Local Heuristic Analysis
                    val analysisResult = PhishingDetector.analyzePackage(this@AppAnalysisActivity, packageName)
                    var riskScore = analysisResult.riskScore
                    val warnings = analysisResult.warnings.toMutableList()

                    // 2. Backend/Cloud Analysis (Only for suspicious apps to save bandwidth)
                    if (riskScore > 0) {
                        try {
                            val file = java.io.File(sourceDir)
                            if (file.exists() && file.canRead()) {
                                val hash = com.example.cyberapp.utils.HashUtils.getSha256(file)
                                val response = com.example.cyberapp.network.RetrofitClient.api.checkApk(com.example.cyberapp.network.ApkCheckRequest(hash))
                                
                                if (response.verdict == "dangerous") {
                                    riskScore += 100
                                    warnings.add(0, "CRITICAL: MALWARE DETECTED BY CLOUD!")
                                }
                            }
                        } catch (e: Exception) {
                            // Network error or file error - ignore and rely on local analysis
                            // e.printStackTrace()
                        }
                    }

                    appListTemp.add(
                        AppInfo(
                            name = appName,
                            packageName = packageName,
                            icon = icon,
                            riskScore = riskScore,
                            sourceDir = sourceDir,
                            analysisWarnings = warnings
                        )
                    )
                }
            }
            
            val sortedApps = appListTemp.sortedByDescending { it.riskScore }

            withContext(Dispatchers.Main) {
                shimmerViewContainer.stopShimmer()
                shimmerViewContainer.visibility = android.view.View.GONE
                appsRecyclerView.visibility = android.view.View.VISIBLE
                
                appList.clear()
                appList.addAll(sortedApps)
                appAdapter.notifyDataSetChanged()
            }
        }
    }
}
