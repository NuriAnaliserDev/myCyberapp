package com.example.cyberapp.modules.session_inspector

import android.os.Bundle
import android.view.View
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

        // Clear list and add only the current, real session
        sessionList.clear()
        sessionList.add(Session(deviceName, "Tashkent, Uzbekistan", ipAddress, "Hozir faol", true))

        // Set up adapter with no click listener, as there are no actions to perform
        adapter = SessionAdapter(sessionList, {})
        recyclerView.adapter = adapter

        // Hide the "Terminate All" button as it's not functional
        findViewById<View>(R.id.btn_terminate_all).visibility = View.GONE
    }
}
