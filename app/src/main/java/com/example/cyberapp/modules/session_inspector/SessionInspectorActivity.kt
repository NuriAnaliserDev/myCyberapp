package com.example.cyberapp.modules.session_inspector

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.R

class SessionInspectorActivity : AppCompatActivity() {

    private lateinit var adapter: SessionAdapter
    private val sessionList = mutableListOf<Session>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_inspector)

        // Setup Header
        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.sessions_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Real Device Info
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        val model = android.os.Build.MODEL
        val deviceName = "$manufacturer $model"
        
        // Get IP Address (Best effort)
        var ipAddress = "127.0.0.1"
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address) {
                        ipAddress = addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) { }

        sessionList.add(Session(deviceName, "Tashkent, Uzbekistan", ipAddress, "Hozir faol", true))
        
        // Add a demo session just for comparison (optional, or remove if user wants strict real data)
        // sessionList.add(Session("Windows 11 (Chrome)", "Samarkand, Uzbekistan", "185.23.12.4", "Active 2h ago"))

        adapter = SessionAdapter(sessionList) { session ->
            showTerminateDialog(session)
        }
        recyclerView.adapter = adapter

        // Terminate All
        findViewById<android.view.View>(R.id.btn_terminate_all).setOnClickListener {
            showTerminateAllDialog()
        }
    }

    private fun showTerminateDialog(session: Session) {
        AlertDialog.Builder(this)
            .setTitle("Terminate Session?")
            .setMessage("Are you sure you want to log out ${session.deviceName}?")
            .setPositiveButton("Terminate") { _, _ ->
                sessionList.remove(session)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Session terminated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTerminateAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Terminate All Sessions?")
            .setMessage("This will log you out from all other devices.")
            .setPositiveButton("Terminate All") { _, _ ->
                val currentSession = sessionList.find { it.isCurrent }
                sessionList.clear()
                if (currentSession != null) {
                    sessionList.add(currentSession)
                }
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "All other sessions terminated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
