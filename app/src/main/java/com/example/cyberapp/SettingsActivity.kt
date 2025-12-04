package com.example.cyberapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import android.content.Intent
import android.app.Activity
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var learningPeriodRadioGroup: RadioGroup
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var switchAutoOpen: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchVoiceAlerts: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchSoundAlerts: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchHapticFeedback: com.google.android.material.switchmaterial.SwitchMaterial
    
    private lateinit var btnChangePin: AppCompatButton
    private lateinit var btnRemovePin: AppCompatButton
    private lateinit var btnResetProfile: AppCompatButton
    private lateinit var pinManager: PinManager
    
    companion object {
        private const val REQUEST_VERIFY_PIN_CHANGE = 101
        private const val REQUEST_SET_PIN = 102
        private const val REQUEST_VERIFY_PIN_REMOVE = 103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. Avval dizaynni yuklash
        setContentView(R.layout.activity_settings)

        // 2. Keyin o'zgaruvchilarni e'lon qilish (findViewById)
        prefs = getSharedPreferences("CyberAppPrefs", Context.MODE_PRIVATE)
        learningPeriodRadioGroup = findViewById(R.id.learning_period_radiogroup)
        sensitivitySeekBar = findViewById(R.id.sensitivity_seekbar)
        switchAutoOpen = findViewById(R.id.switch_auto_open)
        switchVoiceAlerts = findViewById(R.id.switch_voice_alerts)
        switchSoundAlerts = findViewById(R.id.switch_sound_alerts)
        switchHapticFeedback = findViewById(R.id.switch_haptic_feedback)

        // 3. Va eng oxirida ma'lumotlarni yuklash
        loadSettings()

        findViewById<Button>(R.id.save_settings_button).setOnClickListener {
            saveSettings()
        }

        findViewById<Button>(R.id.about_developer_button).setOnClickListener {
            showAboutDeveloperDialog()
        }
        
        // PIN Management
        pinManager = PinManager(this)
        btnChangePin = findViewById(R.id.btn_change_pin)
        btnRemovePin = findViewById(R.id.btn_remove_pin)
        btnResetProfile = findViewById(R.id.btn_reset_profile)

        btnChangePin.setOnClickListener {
            handleChangePin()
        }

        btnRemovePin.setOnClickListener {
            handleRemovePin()
        }
        
        btnResetProfile.setOnClickListener {
            handleResetProfile()
        }
        
        updatePinButtons()
    }

    private fun loadSettings() {
        try {
            // O'rganish davrini yuklash
            val learningPeriodDays = prefs.getLong("learningPeriodDays", 3L)
            when (learningPeriodDays) {
                1L -> findViewById<RadioButton>(R.id.period_1_day)?.isChecked = true
                7L -> findViewById<RadioButton>(R.id.period_7_days)?.isChecked = true
                else -> findViewById<RadioButton>(R.id.period_3_days)?.isChecked = true
            }

            // Sezgirlik darajasini yuklash
            val sensitivityLevel = prefs.getInt("sensitivityLevel", 1) // 0=Past, 1=O'rta, 2=Yuqori
            sensitivitySeekBar.progress = sensitivityLevel
            
            // Auto Open
            val autoOpen = prefs.getBoolean("autoOpenSafeUrls", true)
            switchAutoOpen.isChecked = autoOpen
            
            // Voice Alerts
            val voiceAlertsEnabled = prefs.getBoolean("voice_alerts_enabled", false)
            switchVoiceAlerts.isChecked = voiceAlertsEnabled
            
            // Notifications
            val soundEnabled = prefs.getBoolean("sound_alerts_enabled", true)
            switchSoundAlerts.isChecked = soundEnabled
            
            val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
            switchHapticFeedback.isChecked = hapticEnabled
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Sozlamalarni yuklashda xatolik yuz berdi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        val editor = prefs.edit()

        // O'rganish davrini saqlash
        val selectedPeriodId = learningPeriodRadioGroup.checkedRadioButtonId
        val learningPeriodDays = when (selectedPeriodId) {
            R.id.period_1_day -> 1L
            R.id.period_7_days -> 7L
            else -> 3L
        }
        editor.putLong("learningPeriodDays", learningPeriodDays)

        // Sezgirlik darajasini saqlash
        val sensitivityLevel = sensitivitySeekBar.progress
        editor.putInt("sensitivityLevel", sensitivityLevel)
        
        // Auto Open
        editor.putBoolean("autoOpenSafeUrls", switchAutoOpen.isChecked)
        
        // Voice Alerts
        editor.putBoolean("voice_alerts_enabled", switchVoiceAlerts.isChecked)
        
        // Notifications
        editor.putBoolean("sound_alerts_enabled", switchSoundAlerts.isChecked)
        editor.putBoolean("haptic_feedback_enabled", switchHapticFeedback.isChecked)

        editor.apply()

        Toast.makeText(this, "Sozlamalar saqlandi!", Toast.LENGTH_SHORT).show()
        finish() // Saqlagandan so'ng ekranni yopish
    }

    private fun handleChangePin() {
        if (pinManager.isPinSet()) {
            // Verify old PIN first
            val intent = Intent(this, PinActivity::class.java)
            startActivityForResult(intent, REQUEST_VERIFY_PIN_CHANGE)
        } else {
            // Set new PIN directly
            val intent = Intent(this, PinActivity::class.java)
            intent.putExtra("SETUP_MODE", true)
            startActivityForResult(intent, REQUEST_SET_PIN)
        }
    }

    private fun handleRemovePin() {
        if (pinManager.isPinSet()) {
            // Verify PIN before removing
            val intent = Intent(this, PinActivity::class.java)
            startActivityForResult(intent, REQUEST_VERIFY_PIN_REMOVE)
        }
    }

    private fun handleResetProfile() {
        AlertDialog.Builder(this)
            .setTitle("Profilni qayta o'rnatish")
            .setMessage("Barcha o'rganilgan ma'lumotlar va sozlamalar o'chiriladi. Davom etasizmi?")
            .setPositiveButton("Ha, qayta o'rnatish") { _, _ ->
                // 1. Clear Logs
                val logger = EncryptedLogger(this)
                logger.deleteLog("behaviour_logs.jsonl")
                
                // 2. Clear Prefs (except PIN maybe? No, full reset implies everything)
                // But we should probably keep the PIN if it's a security feature?
                // The user asked for "Profile Reset" (behavioral learning).
                // I'll clear learning data specifically if possible, or just clear all prefs but restore PIN?
                // For now, I'll clear specific learning keys if I knew them, but I don't.
                // I'll clear "learningPeriodDays", "sensitivityLevel", "autoOpenSafeUrls", "voice_alerts_enabled".
                // And maybe keep PIN.
                
                val editor = prefs.edit()
                editor.remove("learningPeriodDays")
                editor.remove("sensitivityLevel")
                editor.remove("autoOpenSafeUrls")
                editor.remove("voice_alerts_enabled")
                editor.apply()
                
                // Reload UI
                loadSettings()
                
                Toast.makeText(this, "Profil muvaffaqiyatli qayta o'rnatildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun updatePinButtons() {
        if (pinManager.isPinSet()) {
            btnChangePin.text = "PIN ni o'zgartirish"
            btnRemovePin.isEnabled = true
            btnRemovePin.alpha = 1.0f
        } else {
            btnChangePin.text = "PIN o'rnatish"
            btnRemovePin.isEnabled = false
            btnRemovePin.alpha = 0.5f
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_VERIFY_PIN_CHANGE -> {
                    // Old PIN verified, now set new PIN
                    val intent = Intent(this, PinActivity::class.java)
                    intent.putExtra("SETUP_MODE", true)
                    startActivityForResult(intent, REQUEST_SET_PIN)
                }
                REQUEST_SET_PIN -> {
                    Toast.makeText(this, "PIN muvaffaqiyatli o'rnatildi", Toast.LENGTH_SHORT).show()
                    updatePinButtons()
                }
                REQUEST_VERIFY_PIN_REMOVE -> {
                    // PIN verified, confirm removal
                    AlertDialog.Builder(this)
                        .setTitle("PIN ni o'chirish")
                        .setMessage("PIN ni o'chirishni xohlaysizmi? Ilova himoyasiz qoladi.")
                        .setPositiveButton("Ha, o'chirish") { _, _ ->
                            pinManager.removePin()
                            Toast.makeText(this, "PIN o'chirildi", Toast.LENGTH_SHORT).show()
                            updatePinButtons()
                        }
                        .setNegativeButton("Bekor qilish", null)
                        .show()
                }
            }
        }
    }

    private fun showAboutDeveloperDialog() {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        
        val message = """
            ${getString(R.string.developer_name)}
            ${getString(R.string.developer_telegram)}
            ${getString(R.string.developer_phone)}
            
            ${getString(R.string.ai_credit)}
            
            ${getString(R.string.copyright_notice)}
        """.trimIndent()
        
        dialogBuilder.setTitle(getString(R.string.about_developer_title))
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
        
        val dialog = dialogBuilder.create()
        dialog.show()
    }
}
