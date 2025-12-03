package com.example.cyberapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var learningPeriodRadioGroup: RadioGroup
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var switchAutoOpen: com.google.android.material.switchmaterial.SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. Avval dizaynni yuklash
        setContentView(R.layout.activity_settings)

        // 2. Keyin o'zgaruvchilarni e'lon qilish (findViewById)
        prefs = getSharedPreferences("CyberAppPrefs", Context.MODE_PRIVATE)
        learningPeriodRadioGroup = findViewById(R.id.learning_period_radiogroup)
        sensitivitySeekBar = findViewById(R.id.sensitivity_seekbar)
        switchAutoOpen = findViewById(R.id.switch_auto_open)

        // 3. Va eng oxirida ma'lumotlarni yuklash
        loadSettings()

        findViewById<Button>(R.id.save_settings_button).setOnClickListener {
            saveSettings()
        }

        findViewById<Button>(R.id.about_developer_button).setOnClickListener {
            showAboutDeveloperDialog()
        }
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

        editor.apply()

        Toast.makeText(this, "Sozlamalar saqlandi!", Toast.LENGTH_SHORT).show()
        finish() // Saqlagandan so'ng ekranni yopish
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
