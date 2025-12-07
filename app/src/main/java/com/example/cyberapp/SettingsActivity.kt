package com.example.cyberapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: EncryptedPrefsManager
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
    
    private var learningPeriodChanged = false

    private lateinit var pinActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = EncryptedPrefsManager(this)
        learningPeriodRadioGroup = findViewById(R.id.learning_period_radiogroup)
        sensitivitySeekBar = findViewById(R.id.sensitivity_seekbar)
        switchAutoOpen = findViewById(R.id.switch_auto_open)
        switchVoiceAlerts = findViewById(R.id.switch_voice_alerts)
        switchSoundAlerts = findViewById(R.id.switch_sound_alerts)
        switchHapticFeedback = findViewById(R.id.switch_haptic_feedback)

        loadSettings()

        findViewById<Button>(R.id.save_settings_button).setOnClickListener {
            saveSettings()
        }

        findViewById<Button>(R.id.about_developer_button).setOnClickListener {
            showAboutDeveloperDialog()
        }
        
        pinManager = PinManager(this)
        btnChangePin = findViewById(R.id.btn_change_pin)
        btnRemovePin = findViewById(R.id.btn_remove_pin)
        btnResetProfile = findViewById(R.id.btn_reset_profile)

        setupPinActivityResultLauncher()

        btnChangePin.setOnClickListener {
            handleChangePin()
        }

        btnRemovePin.setOnClickListener {
            showRemovePinConfirmationDialog()
        }
        
        btnResetProfile.setOnClickListener {
            showResetProfileConfirmationDialog()
        }
        
        updatePinButtons()
    }

    private fun setupPinActivityResultLauncher() {
        pinActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val requestType = result.data?.getStringExtra("REQUEST_TYPE")
                when (requestType) {
                    "CHANGE_PIN" -> {
                        val intent = Intent(this, PinActivity::class.java)
                        intent.putExtra("SETUP_MODE", true)
                        pinActivityResultLauncher.launch(intent)
                    }
                    "SET_PIN" -> {
                        Toast.makeText(this, getString(R.string.settings_pin_set_successfully), Toast.LENGTH_SHORT).show()
                        updatePinButtons()
                    }
                    "REMOVE_PIN" -> {
                        pinManager.removePin()
                        Toast.makeText(this, getString(R.string.settings_pin_removed), Toast.LENGTH_SHORT).show()
                        updatePinButtons()
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        val learningPeriodDays = prefs.getLong("learningPeriodDays", 3L)
        when (learningPeriodDays) {
            1L -> findViewById<RadioButton>(R.id.period_1_day)?.isChecked = true
            7L -> findViewById<RadioButton>(R.id.period_7_days)?.isChecked = true
            else -> findViewById<RadioButton>(R.id.period_3_days)?.isChecked = true
        }

        val sensitivityLevel = prefs.getInt("sensitivityLevel", 1)
        sensitivitySeekBar.progress = sensitivityLevel
        
        val autoOpen = prefs.getBoolean("autoOpenSafeUrls", true)
        switchAutoOpen.isChecked = autoOpen
        
        val voiceAlertsEnabled = prefs.getBoolean("voice_alerts_enabled", false)
        switchVoiceAlerts.isChecked = voiceAlertsEnabled
        
        val soundEnabled = prefs.getBoolean("sound_alerts_enabled", true)
        switchSoundAlerts.isChecked = soundEnabled
        
        val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
        switchHapticFeedback.isChecked = hapticEnabled
    }

    private fun saveSettings() {
        val editor = prefs.edit()

        val selectedPeriodId = learningPeriodRadioGroup.checkedRadioButtonId
        val learningPeriodDays = when (selectedPeriodId) {
            R.id.period_1_day -> 1L
            R.id.period_7_days -> 7L
            else -> 3L
        }
        
        if (prefs.getLong("learningPeriodDays", 3L) != learningPeriodDays) {
            learningPeriodChanged = true
        }
        editor.putLong("learningPeriodDays", learningPeriodDays)

        val sensitivityLevel = sensitivitySeekBar.progress
        editor.putInt("sensitivityLevel", sensitivityLevel)
        
        editor.putBoolean("autoOpenSafeUrls", switchAutoOpen.isChecked)
        
        editor.putBoolean("voice_alerts_enabled", switchVoiceAlerts.isChecked)
        
        editor.putBoolean("sound_alerts_enabled", switchSoundAlerts.isChecked)
        editor.putBoolean("haptic_feedback_enabled", switchHapticFeedback.isChecked)

        editor.apply()

        if (learningPeriodChanged) {
            showRestartDialog()
        } else {
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun showRestartDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_restart_required_title))
            .setMessage(getString(R.string.settings_restart_required_message))
            .setPositiveButton(getString(R.string.settings_restart_now)) { _, _ ->
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                val componentName = intent!!.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                startActivity(mainIntent)
                System.exit(0)
            }
            .setNegativeButton(getString(R.string.settings_restart_later)) { _, _ ->
                 Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                 finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleChangePin() {
        if (pinManager.isPinSet()) {
            val intent = Intent(this, PinActivity::class.java)
            intent.putExtra("REQUEST_TYPE", "CHANGE_PIN")
            pinActivityResultLauncher.launch(intent)
        } else {
            val intent = Intent(this, PinActivity::class.java)
            intent.putExtra("SETUP_MODE", true)
            intent.putExtra("REQUEST_TYPE", "SET_PIN")
            pinActivityResultLauncher.launch(intent)
        }
    }

    private fun showRemovePinConfirmationDialog() {
        if (pinManager.isPinSet()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_remove_pin_confirmation_title))
                .setMessage(getString(R.string.settings_remove_pin_confirmation_message))
                .setPositiveButton(getString(R.string.settings_confirm_remove)) { _, _ ->
                    handleRemovePin()
                }
                .setNegativeButton(getString(R.string.settings_cancel), null)
                .show()
        }
    }
    
    private fun handleRemovePin() {
        if (pinManager.isPinSet()) {
            val intent = Intent(this, PinActivity::class.java)
            intent.putExtra("REQUEST_TYPE", "REMOVE_PIN")
            pinActivityResultLauncher.launch(intent)
        }
    }
    
    private fun showResetProfileConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_reset_profile_confirmation_title))
            .setMessage(getString(R.string.settings_reset_profile_confirmation_message))
            .setPositiveButton(getString(R.string.settings_confirm_reset)) { _, _ ->
                handleResetProfile()
            }
            .setNegativeButton(getString(R.string.settings_cancel), null)
            .show()
    }

    private fun handleResetProfile() {
        val editor = prefs.edit()
        editor.remove("learningPeriodDays")
        editor.remove("sensitivityLevel")
        editor.remove("autoOpenSafeUrls")
        editor.remove("voice_alerts_enabled")
        editor.apply()
        
        loadSettings()
        
        Toast.makeText(this, getString(R.string.settings_profile_reset_successfully), Toast.LENGTH_SHORT).show()
    }

    private fun updatePinButtons() {
        if (pinManager.isPinSet()) {
            btnChangePin.text = getString(R.string.settings_change_pin)
            btnRemovePin.isEnabled = true
            btnRemovePin.alpha = 1.0f
        } else {
            btnChangePin.text = getString(R.string.settings_set_pin)
            btnRemovePin.isEnabled = false
            btnRemovePin.alpha = 0.5f
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
