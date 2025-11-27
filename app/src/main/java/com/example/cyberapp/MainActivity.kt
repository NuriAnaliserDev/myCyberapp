package com.example.cyberapp

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), AnomalyAdapter.OnAnomalyInteractionListener {
    private val TAG = "MainActivity"
    private val LOG_FILE_NAME = "behaviour_logs.jsonl"
    private lateinit var anomaliesRecyclerView: RecyclerView
    private lateinit var statusTextView: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var anomalyAdapter: AnomalyAdapter
    private val anomalyList = mutableListOf<Anomaly>()
    private lateinit var vpnSwitch: MaterialSwitch

    //<editor-fold desc="Lifecycle and Launchers">
    private val vpnAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Tarmoq himoyasi uchun ruxsat zarur!", Toast.LENGTH_LONG).show()
            vpnSwitch.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("CyberAppPrefs", Context.MODE_PRIVATE)
        statusTextView = findViewById(R.id.status_textview)
        vpnSwitch = findViewById(R.id.vpn_switch)
        setupRecyclerView()
        setupButtonsAndListeners()
        updateStatusView()
        updateAnomaliesView()
    }
    //</editor-fold>

    //<editor-fold desc="Setup and Listeners - FINAL VERSION">
    private fun setupButtonsAndListeners() {
        findViewById<Button>(R.id.start_logger_button).setOnClickListener { startLogger() }
        findViewById<Button>(R.id.stop_logger_button).setOnClickListener { stopLogger() }
        findViewById<Button>(R.id.settings_button).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.refresh_anomalies_button).setOnClickListener { updateAnomaliesView() }
        findViewById<Button>(R.id.reset_profile_button).setOnClickListener { confirmAndResetProfile() }
        findViewById<Button>(R.id.app_analysis_button).setOnClickListener { startActivity(Intent(this, AppAnalysisActivity::class.java)) }

        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                prepareAndStartVpn()
            } else {
                stopVpnService()
            }
        }
        
        checkPermissions()
        checkAndRequestUsageStatsPermission()
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(android.Manifest.permission.READ_PHONE_STATE)
        permissions.add(android.Manifest.permission.BODY_SENSORS)
        
        val missingPermissions = permissions.filter { 
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED 
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 1001)
        }
    }
    //</editor-fold>

    //<editor-fold desc="VPN Logic - FIXED">
    private fun prepareAndStartVpn() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnAuthLauncher.launch(vpnIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, CyberVpnService::class.java).apply {
            action = CyberVpnService.ACTION_CONNECT // TO'G'RILANDI
        }
        startService(intent)
        vpnSwitch.isChecked = true
    }

    private fun stopVpnService() {
        val intent = Intent(this, CyberVpnService::class.java).apply {
            action = CyberVpnService.ACTION_DISCONNECT // TO'G'RILANDI
        }
        startService(intent)
        vpnSwitch.isChecked = false
    }
    //</editor-fold>

    // ... (The rest of the MainActivity code is correct and unchanged)
    //<editor-fold desc="Unchanged Code">
    private fun setupRecyclerView() { anomaliesRecyclerView = findViewById(R.id.anomalies_recyclerview); anomalyAdapter = AnomalyAdapter(anomalyList, this); anomaliesRecyclerView.adapter = anomalyAdapter; anomaliesRecyclerView.layoutManager = LinearLayoutManager(this); }
    override fun onMarkAsNormal(anomaly: Anomaly, position: Int) { try { val json = JSONObject(anomaly.rawJson); val description = json.getString("description"); val exceptionKey = "exception_" + description.replace(" ", "_").take(50); prefs.edit().putBoolean(exceptionKey, true).apply(); anomalyList.removeAt(position); anomalyAdapter.notifyItemRemoved(position); anomalyAdapter.notifyItemRangeChanged(position, anomalyList.size); Toast.makeText(this, "Istisno saqlandi", Toast.LENGTH_SHORT).show(); } catch (e: Exception) { Log.e(TAG, "Failed to mark as normal: ${e.message}"); } }
    private fun confirmAndResetProfile() { AlertDialog.Builder(this).setTitle("Profilni O\'chirish").setMessage("Barcha o\'rganilgan ma\'lumotlarni o\'chirib, qayta o\'rganish rejimini boshlaysizmi?").setPositiveButton("Ha") { _, _ -> resetProfile() }.setNegativeButton("Yo\'q", null).show(); }
    private fun resetProfile() { stopLogger(); stopVpnService(); Toast.makeText(this, "Profil tozalanmoqda...", Toast.LENGTH_SHORT).show(); thread { prefs.edit().clear().apply(); File(filesDir, LOG_FILE_NAME).delete(); runOnUiThread { Toast.makeText(this, "Profil tozalandi. Ilovani qayta ishga tushiring.", Toast.LENGTH_LONG).show(); updateStatusView(); updateAnomaliesView(); } } }
    private fun startLogger() { val serviceIntent = Intent(this, LoggerService::class.java); androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent); }
    private fun stopLogger() { val serviceIntent = Intent(this, LoggerService::class.java); stopService(serviceIntent); }
    private fun updateStatusView() { val isProfileCreated = prefs.getBoolean("isProfileCreated", false); statusTextView.text = if (isProfileCreated) { val profileTime = prefs.getLong("profileCreationTime", 0); "Holat: Himoya faol (Profil: ${formatDate(profileTime)})" } else { val firstLaunch = prefs.getLong("firstLaunchTime", 0); if (firstLaunch == 0L) { prefs.edit().putLong("firstLaunchTime", System.currentTimeMillis()).apply() }; "Holat: O\'rganish rejimi..." } }
    private fun updateAnomaliesView() { thread { val newAnomalies = mutableListOf<Anomaly>(); val logFile = File(filesDir, LOG_FILE_NAME); if (logFile.exists()) { val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()); try { logFile.bufferedReader().useLines { lines -> lines.forEach { line -> try { val json = JSONObject(line); val type = json.getString("type"); if (type == "ANOMALY" || type == "ANOMALY_NETWORK") { val description = json.getString("description"); val exceptionKey = "exception_" + description.replace(" ", "_").take(50); if (!prefs.getBoolean(exceptionKey, false)) { val timestamp = json.getLong("timestamp"); newAnomalies.add(Anomaly(dateFormat.format(Date(timestamp)), description, line)); } } } catch (e: Exception) {} } } } catch (e: Exception) { Log.e(TAG, "Error reading anomalies: ${e.message}") } }; runOnUiThread { anomalyList.clear(); if (newAnomalies.isNotEmpty()) { anomalyList.addAll(newAnomalies.asReversed()); }; anomalyAdapter.notifyDataSetChanged(); } } }
    private fun formatDate(timestamp: Long): String { return SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp)) }
    private fun checkAndRequestUsageStatsPermission() { if (!hasUsageStatsPermission()) { Toast.makeText(this, "Ilovalar statistikasini kuzatish uchun ruxsat bering", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } else { Toast.makeText(this, "Ruxsatnoma allaqachon berilgan!", Toast.LENGTH_SHORT).show(); } }
    private fun hasUsageStatsPermission(): Boolean { val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) } else { appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) }; return mode == AppOpsManager.MODE_ALLOWED; }
    //</editor-fold>
}
