package com.example.cyberapp

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.modules.apk_scanner.AppAdapter
import com.example.cyberapp.modules.apk_scanner.AppInfo
import com.facebook.shimmer.ShimmerFrameLayout
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
    private lateinit var shimmerViewContainer: ShimmerFrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_analysis)

        val titleText = intent.getStringExtra("TITLE") ?: "App Analysis"
        findViewById<TextView>(R.id.header_title).text = titleText
        shimmerViewContainer = findViewById(R.id.shimmer_view_container)

        findViewById<View>(R.id.btn_back).setOnClickListener {
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
        shimmerViewContainer.startShimmer()
        shimmerViewContainer.visibility = View.VISIBLE
        appsRecyclerView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val appListTemp = mutableListOf<AppInfo>()
            try {
                val pm = packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

                for (packageInfo in packages) {
                    try {
                        val appInfo = packageInfo.applicationInfo ?: continue
                        val appName = appInfo.loadLabel(pm).toString()
                        val packageName = packageInfo.packageName
                        
                        // Faqatgina asosiy (non-system) ilovalarni ko'rsatish
                        if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                            val analysisResult = PhishingDetector.analyzePackage(this@AppAnalysisActivity, packageName)
                            val riskScore = analysisResult.riskScore
                            val warnings = analysisResult.warnings.toMutableList()

                            // // VAQTINCHA O'CHIRILDI: Bulutli tahlil
                            // if (riskScore > 0) {
                            //     try {
                            //         val file = File(appInfo.sourceDir)
                            //         if (file.exists() && file.canRead()) {
                            //             if (file.length() > 300 * 1024 * 1024) { // 300 MB limit
                            //                 warnings.add("OGOHLANTIRISH: Ilova hajmi juda katta, bulutli tahlil o'tkazib yuborildi.")
                            //             } else {
                            //                 val hash = getFileSha256(file)
                            //                 val response = com.example.cyberapp.network.RetrofitClient.api.checkApk(com.example.cyberapp.network.ApkCheckRequest(hash))
                            //                 if (response.verdict == "dangerous") {
                            //                     riskScore += 100
                            //                     warnings.add(0, "CRITICAL: MALWARE DETECTED BY CLOUD!")
                            //                 }
                            //             }
                            //         }
                            //     } catch (e: Exception) {
                            //         // Handle cloud scan exceptions silently for now
                            //     }
                            // }

                            appListTemp.add(
                                AppInfo(
                                    name = appName,
                                    packageName = packageName,
                                    riskScore = riskScore,
                                    sourceDir = appInfo.sourceDir,
                                    analysisWarnings = warnings
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Bu ilovani o'tkazib yuborish
                        continue
                    }
                }
            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppAnalysisActivity, "Ilovalarni yuklashda xatolik: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            val sortedApps = appListTemp.sortedByDescending { it.riskScore }

            withContext(Dispatchers.Main) {
                shimmerViewContainer.stopShimmer()
                shimmerViewContainer.visibility = View.GONE
                appsRecyclerView.visibility = View.VISIBLE
                
                appList.clear()
                appList.addAll(sortedApps)
                appAdapter.notifyDataSetChanged()
                
                if (appList.isEmpty()) {
                    Toast.makeText(this@AppAnalysisActivity, "Tahlil uchun ilovalar topilmadi.", Toast.LENGTH_SHORT).show()
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
