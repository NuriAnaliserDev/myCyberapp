package com.example.cyberapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

class SettingsActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private lateinit var pinSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var biometricSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var voiceAlertsSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var changePinButton: Button
    
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var encryptedLogger: EncryptedLogger
    private lateinit var learningPeriodRadioGroup: RadioGroup
    private lateinit var sensitivitySeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = EncryptedPrefsManager(this)
        encryptedLogger = EncryptedLogger(this)
        pinManager = PinManager(this)

        learningPeriodRadioGroup = findViewById(R.id.learning_period_radiogroup)
        sensitivitySeekBar = findViewById(R.id.sensitivity_seekbar)
        pinSwitch = findViewById(R.id.pin_switch)
        biometricSwitch = findViewById(R.id.biometric_switch)
        voiceAlertsSwitch = findViewById(R.id.voice_alerts_switch)
        changePinButton = findViewById(R.id.change_pin_button)

        loadSettings()

        findViewById<Button>(R.id.save_settings_button).setOnClickListener {
            saveSettings()
        }
        findViewById<Button>(R.id.clear_exceptions_button).setOnClickListener {
            confirmClearExceptions()
        }
        findViewById<Button>(R.id.share_crash_logs_button).setOnClickListener {
            shareCrashLogs()
        }
        
        setupSecurityListeners()
        
        findViewById<android.widget.TextView>(R.id.developer_telegram).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/m1nimalism_co1"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Telegram ochilmadi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSecurityListeners() {
        pinSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!pinManager.isPinSet()) {
                    // Start PIN setup
                    val intent = Intent(this, PinActivity::class.java)
                    intent.putExtra("SETUP_MODE", true)
                    startActivityForResult(intent, 1001)
                }
                updateSecurityUI()
            } else {
                // Confirm before disabling
                if (pinManager.isPinSet()) {
                    // Ask for current PIN before disabling
                    val intent = Intent(this, PinActivity::class.java)
                    intent.putExtra("SETUP_MODE", false) // Verify mode
                    startActivityForResult(intent, 1002)
                }
            }
        }

        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.putBoolean("biometric_enabled", isChecked)
        }

        voiceAlertsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.putBoolean("voice_alerts_enabled", isChecked)
        }

        changePinButton.setOnClickListener {
            val intent = Intent(this, PinActivity::class.java)
            intent.putExtra("SETUP_MODE", true)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) { // Setup PIN
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "PIN kod o'rnatildi", Toast.LENGTH_SHORT).show()
            } else {
                // User cancelled setup, revert switch
                pinSwitch.isChecked = false
            }
        } else if (requestCode == 1002) { // Disable PIN
            if (resultCode == RESULT_OK) {
                pinManager.removePin()
                Toast.makeText(this, "PIN kod o'chirildi", Toast.LENGTH_SHORT).show()
            } else {
                // User cancelled or failed verification, revert switch
                pinSwitch.isChecked = true
            }
        }
        updateSecurityUI()
    }

    private fun loadSettings() {
        // O'rganish davrini yuklash
        val learningPeriodDays = prefs.getLong("learningPeriodDays", 3L)
        when (learningPeriodDays) {
            1L -> findViewById<RadioButton>(R.id.period_1_day).isChecked = true
            7L -> findViewById<RadioButton>(R.id.period_7_days).isChecked = true
            else -> findViewById<RadioButton>(R.id.period_3_days).isChecked = true
        }

        // Sezgirlik darajasini yuklash
        val sensitivityLevel = prefs.getInt("sensitivityLevel", 1) // 0=Past, 1=O'rta, 2=Yuqori
        sensitivitySeekBar.progress = sensitivityLevel

        updateSecurityUI()
    }

    private fun updateSecurityUI() {
        val isPinSet = pinManager.isPinSet()
        
        // Avoid triggering listener loops
        pinSwitch.setOnCheckedChangeListener(null)
        pinSwitch.isChecked = isPinSet
        pinSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!pinManager.isPinSet()) {
                    val intent = Intent(this, PinActivity::class.java)
                    intent.putExtra("SETUP_MODE", true)
                    startActivityForResult(intent, 1001)
                }
            } else {
                if (pinManager.isPinSet()) {
                    val intent = Intent(this, PinActivity::class.java)
                    intent.putExtra("SETUP_MODE", false)
                    startActivityForResult(intent, 1002)
                }
            }
        }

        biometricSwitch.isEnabled = isPinSet
        biometricSwitch.isChecked = prefs.getBoolean("biometric_enabled", false)
        
        voiceAlertsSwitch.isChecked = prefs.getBoolean("voice_alerts_enabled", false)
        
        changePinButton.visibility = if (isPinSet) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun saveSettings() {
        val editor = prefs.edit()

        // O'rganish davrini saqlash
        val selectedPeriodId = learningPeriodRadioGroup.checkedRadioButtonId
        if (selectedPeriodId == -1) {
            Toast.makeText(this, "Iltimos, o'rganish davrini tanlang!", Toast.LENGTH_SHORT).show()
            return
        }

        val learningPeriodDays = when (selectedPeriodId) {
            R.id.period_1_day -> 1L
            R.id.period_7_days -> 7L
            else -> 3L
        }
        editor.putLong("learningPeriodDays", learningPeriodDays)

        // Sezgirlik darajasini saqlash
        val sensitivityLevel = sensitivitySeekBar.progress
        if (sensitivityLevel !in 0..2) {
            Toast.makeText(this, "Sezgirlik darajasi noto'g'ri!", Toast.LENGTH_SHORT).show()
            return
        }
        editor.putInt("sensitivityLevel", sensitivityLevel)

        editor.apply()

        Toast.makeText(this, "Sozlamalar saqlandi!", Toast.LENGTH_SHORT).show()
        finish() // Saqlagandan so'ng ekranni yopish
    }

    private fun confirmClearExceptions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_exceptions_confirm_title)
            .setMessage(R.string.settings_clear_exceptions_confirm_body)
            .setPositiveButton(android.R.string.ok) { _, _ -> clearExceptions() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearExceptions() {
        val editor = prefs.edit()
        val keysToRemove = prefs.getAll().keys.filter { it.startsWith("exception_") }
        keysToRemove.forEach { editor.remove(it) }
        editor.apply()
        Toast.makeText(this, R.string.settings_clear_exceptions_success, Toast.LENGTH_SHORT).show()
    }

    private fun shareCrashLogs() {
        try {
            val logs = encryptedLogger.readLog("crash_logs.txt")
            if (logs.isBlank()) {
                Toast.makeText(this, R.string.settings_share_crash_logs_empty, Toast.LENGTH_SHORT).show()
                return
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_share_crash_logs_title))
                putExtra(Intent.EXTRA_TEXT, logs)
            }
            startActivity(Intent.createChooser(sendIntent, getString(R.string.settings_share_crash_logs_title)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.settings_share_crash_logs_error, Toast.LENGTH_SHORT).show()
        }
    }
}
