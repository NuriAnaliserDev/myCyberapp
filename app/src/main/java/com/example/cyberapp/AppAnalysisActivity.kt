package com.example.cyberapp

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.modules.apk_scanner.AppAdapter
import com.example.cyberapp.modules.apk_scanner.AppInfo
import com.facebook.shimmer.ShimmerFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppAnalysisActivity : AppCompatActivity() {

    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private val appList = mutableListOf<AppInfo>()
    private lateinit var shimmerViewContainer: ShimmerFrameLayout
    private var isPermissionManagerMode = false
    private val tag = "AppAnalysisActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_analysis)

        val titleText = intent.getStringExtra("TITLE") ?: getString(R.string.app_analysis_title)
        isPermissionManagerMode = titleText == getString(R.string.permission_manager)

        findViewById<TextView>(R.id.header_title).text = titleText
        shimmerViewContainer = findViewById(R.id.shimmer_view_container)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

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
        shimmerViewContainer.startShimmer()
        shimmerViewContainer.isVisible = true
        appsRecyclerView.isGone = true

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

                        if (isPermissionManagerMode) {
                            val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                            if (requestedPermissions.isNotEmpty()) {
                                appListTemp.add(
                                    AppInfo(
                                        name = appName,
                                        packageName = packageName,
                                        riskScore = 0, // Not calculated in this mode
                                        sourceDir = appInfo.sourceDir,
                                        analysisWarnings = requestedPermissions.toMutableList()
                                    )
                                )
                            }
                        } else {
                            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                                val analysisResult = PhishingDetector.analyzePackage(this@AppAnalysisActivity, packageName)
                                val riskScore = analysisResult.riskScore
                                val warnings = analysisResult.warnings.toMutableList()

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
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to process package: ${packageInfo.packageName}", e)
                        continue
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppAnalysisActivity, getString(R.string.error_loading_apps, e.message), Toast.LENGTH_LONG).show()
                }
            }

            val sortedApps = if (isPermissionManagerMode) appListTemp else appListTemp.sortedByDescending { it.riskScore }

            withContext(Dispatchers.Main) {
                shimmerViewContainer.stopShimmer()
                shimmerViewContainer.isGone = true
                appsRecyclerView.isVisible = true

                val previousSize = appList.size
                appList.clear()
                appAdapter.notifyItemRangeRemoved(0, previousSize)
                appList.addAll(sortedApps)
                appAdapter.notifyItemRangeInserted(0, sortedApps.size)

                if (appList.isEmpty()) {
                    Toast.makeText(this@AppAnalysisActivity, getString(R.string.no_apps_for_analysis), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
