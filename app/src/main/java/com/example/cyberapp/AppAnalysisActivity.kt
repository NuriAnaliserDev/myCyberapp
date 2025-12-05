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
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

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
            try {
                val pm = packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                val appListTemp = mutableListOf<AppInfo>()

                for (packageInfo in packages) {
                    packageInfo.applicationInfo?.let { appInfo ->
                        try {
                            val appName = appInfo.loadLabel(pm).toString()
                            val packageName = packageInfo.packageName
                            val sourceDir = appInfo.sourceDir
                            
                            val analysisResult = PhishingDetector.analyzePackage(this@AppAnalysisActivity, packageName)
                            var riskScore = analysisResult.riskScore
                            val warnings = analysisResult.warnings.toMutableList()

                            if (riskScore > 0) {
                                try {
                                    val file = File(sourceDir)
                                    if (file.exists() && file.canRead()) {
                                        if (file.length() > 300 * 1024 * 1024) { // 300 MB limit
                                            warnings.add("OGOHLANTIRISH: Ilova hajmi juda katta, bulutli tahlil o'tkazib yuborildi.")
                                        } else {
                                            val hash = getFileSha256(file)
                                            val response = com.example.cyberapp.network.RetrofitClient.api.checkApk(com.example.cyberapp.network.ApkCheckRequest(hash))
                                            
                                            if (response.verdict == "dangerous") {
                                                riskScore += 100
                                                warnings.add(0, "CRITICAL: MALWARE DETECTED BY CLOUD!")
                                            }
                                        }
                                    }
                                } catch (e: OutOfMemoryError) {
                                    warnings.add("XATOLIK: Xotira yetishmovchiligi tufayli to'liq tahlil qilinmadi.")
                                    System.gc()
                                } catch (e: Exception) {
                                    // Handle other exceptions
                                }
                            }

                            appListTemp.add(
                                AppInfo(
                                    name = appName,
                                    packageName = packageName,
                                    riskScore = riskScore,
                                    sourceDir = sourceDir,
                                    analysisWarnings = warnings
                                )
                            )
                        } catch (e: Exception) {
                            // Skip this app if analysis fails
                        }
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
                    
                    if (appList.isEmpty()) {
                        android.widget.Toast.makeText(this@AppAnalysisActivity, "Ilovalar topilmadi", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    shimmerViewContainer.stopShimmer()
                    shimmerViewContainer.visibility = android.view.View.GONE
                    android.widget.Toast.makeText(this@AppAnalysisActivity, "Xatolik yuz berdi: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
