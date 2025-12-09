package com.example.cyberapp.modules.session_inspector

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyberapp.R
import com.example.cyberapp.EncryptedPrefsManager
import com.example.cyberapp.network.AuthManager
import com.example.cyberapp.network.RetrofitClient
import com.example.cyberapp.network.Session as NetworkSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionInspectorActivity : AppCompatActivity() {

    private lateinit var adapter: SessionAdapter
    private val sessionList = mutableListOf<Session>()
    private lateinit var prefs: EncryptedPrefsManager
    private var currentSessionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_inspector)

        prefs = EncryptedPrefsManager(this)

        // Setup Header
        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.sessions_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Set up adapter with terminate action
        adapter = SessionAdapter(sessionList) { session ->
            handleTerminateSession(session)
        }
        recyclerView.adapter = adapter

        // Setup "Terminate All" button
        findViewById<View>(R.id.btn_terminate_all).setOnClickListener {
            handleTerminateAllSessions()
        }

        // Load sessions from backend
        lifecycleScope.launch {
            loadSessions()
        }
    }
    
    private suspend fun loadSessions() {
        try {
            withContext(Dispatchers.IO) {
                val token = AuthManager.getAuthToken(prefs)
                
                if (token != null) {
                    // Authenticated - load from backend
                    try {
                        val response = RetrofitClient.api.listSessions("Bearer $token")
                        sessionList.clear()
                        
                        // Convert network sessions to local sessions
                        response.sessions.forEach { networkSession ->
                            val isCurrent = networkSession.session_id == currentSessionId
                            sessionList.add(
                                Session(
                                    networkSession.device_name,
                                    getString(R.string.location_tashkent),
                                    networkSession.ip_address,
                                    if (isCurrent) getString(R.string.active_now_this_device) else getString(R.string.active),
                                    isCurrent,
                                    isCurrent
                                )
                            )
                        }
                        
                        // If no sessions, create current session
                        if (sessionList.isEmpty()) {
                            createCurrentSession()
                        }
                    } catch (e: Exception) {
                        // Backend error, fallback to local
                        createCurrentSession()
                    }
                } else {
                    // Not authenticated, use local session
                    createCurrentSession()
                }
            }
            
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                updateButtonVisibility()
            }
        } catch (e: Exception) {
            // Error loading, use local session
            createCurrentSession()
            adapter.notifyDataSetChanged()
            updateButtonVisibility()
        }
    }
    
    private suspend fun createCurrentSession() {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() 
        }
        val model = android.os.Build.MODEL
        val deviceName = "$manufacturer $model"
        
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
        
        sessionList.clear()
        sessionList.add(
            Session(
                deviceName,
                getString(R.string.location_tashkent),
                ipAddress,
                getString(R.string.active_now_this_device),
                true,
                true
            )
        )
        
        // Try to create session on backend
        val token = AuthManager.getAuthToken(prefs)
        if (token != null) {
            try {
                withContext(Dispatchers.IO) {
                    val deviceInfo = mapOf(
                        "manufacturer" to android.os.Build.MANUFACTURER,
                        "model" to android.os.Build.MODEL,
                        "android_version" to android.os.Build.VERSION.RELEASE
                    )
                    val networkSession = RetrofitClient.api.createSession(
                        com.example.cyberapp.network.SessionCreateRequest(
                            deviceName,
                            deviceInfo,
                            ipAddress
                        ),
                        "Bearer $token"
                    )
                    currentSessionId = networkSession.session_id
                }
            } catch (e: Exception) {
                // Ignore creation errors
            }
        }
    }
    
    private fun updateButtonVisibility() {
        val hasOtherSessions = sessionList.any { !it.isCurrentDevice }
        findViewById<View>(R.id.btn_terminate_all).visibility = if (hasOtherSessions) View.VISIBLE else View.GONE
    }
    
    private fun handleTerminateSession(session: Session) {
        if (session.isCurrentDevice) {
            Toast.makeText(this, getString(R.string.cannot_terminate_current_session), Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.terminate_session_title))
            .setMessage(getString(R.string.terminate_session_message, session.deviceName))
            .setPositiveButton(getString(R.string.terminate)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val token = AuthManager.getAuthToken(prefs)
                        if (token != null) {
                            // Find session ID from network sessions
                            val sessionId = sessionList.indexOf(session) + 1 // Simplified
                            withContext(Dispatchers.IO) {
                                try {
                                    RetrofitClient.api.terminateSession(sessionId, "Bearer $token")
                                } catch (e: Exception) {
                                    // Ignore backend errors, still remove locally
                                }
                            }
                        }
                        
                        sessionList.remove(session)
                        adapter.notifyDataSetChanged()
                        updateButtonVisibility()
                        
                        Toast.makeText(this@SessionInspectorActivity, 
                            getString(R.string.session_terminated_success, session.deviceName), 
                            Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@SessionInspectorActivity, 
                            "Xatolik: ${e.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun handleTerminateAllSessions() {
        val otherSessions = sessionList.filter { !it.isCurrentDevice }
        if (otherSessions.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_other_sessions), Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.terminate_all_sessions_title))
            .setMessage(getString(R.string.terminate_all_sessions_message, otherSessions.size))
            .setPositiveButton(getString(R.string.terminate_all)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val token = AuthManager.getAuthToken(prefs)
                        if (token != null) {
                            withContext(Dispatchers.IO) {
                                try {
                                    RetrofitClient.api.terminateAllSessions(
                                        excludeSessionId = currentSessionId,
                                        token = "Bearer $token"
                                    )
                                } catch (e: Exception) {
                                    // Ignore backend errors
                                }
                            }
                        }
                        
                        sessionList.removeAll(otherSessions)
                        adapter.notifyDataSetChanged()
                        updateButtonVisibility()
                        
                        Toast.makeText(this@SessionInspectorActivity, 
                            getString(R.string.all_sessions_terminated_success), 
                            Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@SessionInspectorActivity, 
                            "Xatolik: ${e.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
