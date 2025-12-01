package com.example.cyberapp.modules.apk_scanner

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.R
import com.example.cyberapp.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppScanActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_scan)

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        recyclerView.layoutManager = LinearLayoutManager(this)

        scanApps()
    }

    private fun scanApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val appList = mutableListOf<AppInfo>()

            for (packageInfo in installedApps) {
                // Skip system apps for now (optional)
                // if ((packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

                val appName = packageInfo.applicationInfo.loadLabel(packageManager).toString()
                val packageName = packageInfo.packageName
                val icon = packageInfo.applicationInfo.loadIcon(packageManager)
                val sourceDir = packageInfo.applicationInfo.sourceDir

                val dangerousPermissions = mutableListOf<String>()
                var riskScore = 0

                packageInfo.requestedPermissions?.forEach { permission ->
                    if (PermissionUtils.isDangerous(permission)) {
                        dangerousPermissions.add(permission.substringAfterLast("."))
                        riskScore += 10
                    }
                }

                // Calculate Hash and Check Backend (Only for apps with some risk or all)
                // For performance, we might want to do this only if riskScore > 0 or on demand.
                // For this demo, let's do it for apps with riskScore > 0 to save time.
                if (riskScore > 0) {
                    try {
                        val hash = com.example.cyberapp.utils.HashUtils.getSha256(java.io.File(sourceDir).readBytes())
                        val response = com.example.cyberapp.network.RetrofitClient.api.checkApk(com.example.cyberapp.network.ApkCheckRequest(hash))
                        if (response.verdict == "dangerous") {
                            riskScore += 100
                            dangerousPermissions.add("MALWARE DETECTED")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    appList.add(AppInfo(appName, packageName, icon, riskScore, dangerousPermissions))
                }
            }

            // Sort by risk score
            appList.sortByDescending { it.riskScore }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                adapter = AppAdapter(appList)
                recyclerView.adapter = adapter
            }
        }
    }
}
