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

        // Simulated Data
        sessionList.add(Session("Android 14 (Pixel 7)", "Tashkent, Uzbekistan", "192.168.1.105", "Active Now", true))
        sessionList.add(Session("Windows 11 (Chrome)", "Samarkand, Uzbekistan", "185.23.12.4", "Active 2h ago"))
        sessionList.add(Session("iPhone 13 (Safari)", "Tashkent, Uzbekistan", "178.21.55.1", "Active 5h ago"))
        sessionList.add(Session("Linux (Firefox)", "Unknown Location", "45.32.11.9", "Active 1d ago"))

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
