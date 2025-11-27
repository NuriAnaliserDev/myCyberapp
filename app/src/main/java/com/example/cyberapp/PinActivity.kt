package com.example.cyberapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PinActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private val currentPin = StringBuilder()
    private val dots = mutableListOf<ImageView>()
    private var isSetupMode = false
    private var firstPinAttempt = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        pinManager = PinManager(this)
        isSetupMode = intent.getBooleanExtra("SETUP_MODE", false)

        setupViews()
        updateStatusText()
    }

    private fun setupViews() {
        val dotsContainer = findViewById<android.view.ViewGroup>(R.id.pin_dots_container)
        for (i in 0 until dotsContainer.childCount) {
            dots.add(dotsContainer.getChildAt(i) as ImageView)
        }

        val gridLayout = findViewById<android.widget.GridLayout>(R.id.keypad_grid)
        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)
            if (view is Button) {
                view.setOnClickListener { onDigitClick(view.text.toString()) }
            } else if (view.id == R.id.btn_delete) {
                view.setOnClickListener { onDeleteClick() }
            } else if (view.id == R.id.btn_fingerprint) {
                view.setOnClickListener { 
                    setResult(Activity.RESULT_CANCELED)
                    finish() 
                }
            }
        }
    }

    private fun updateStatusText() {
        val statusText = findViewById<TextView>(R.id.pin_status)
        if (isSetupMode) {
            statusText.text = if (firstPinAttempt.isEmpty()) "Yangi PIN o'rnating" else "PINni tasdiqlang"
        } else {
            statusText.text = "PIN kodni kiriting"
        }
    }

    private fun onDigitClick(digit: String) {
        if (currentPin.length < 4) {
            currentPin.append(digit)
            updateDots()
            vibrate()
            
            if (currentPin.length == 4) {
                handlePinComplete()
            }
        }
    }

    private fun onDeleteClick() {
        if (currentPin.isNotEmpty()) {
            currentPin.deleteCharAt(currentPin.length - 1)
            updateDots()
            vibrate()
        }
    }

    private fun updateDots() {
        for (i in dots.indices) {
            if (i < currentPin.length) {
                dots[i].setImageResource(R.drawable.ic_dot_filled)
            } else {
                dots[i].setImageResource(R.drawable.ic_dot_empty)
            }
        }
    }

    private fun handlePinComplete() {
        val enteredPin = currentPin.toString()
        currentPin.clear()
        updateDots()

        if (isSetupMode) {
            if (firstPinAttempt.isEmpty()) {
                firstPinAttempt = enteredPin
                updateStatusText()
            } else {
                if (enteredPin == firstPinAttempt) {
                    pinManager.setPin(enteredPin)
                    Toast.makeText(this, "PIN o'rnatildi", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    firstPinAttempt = ""
                    shakeDots()
                    Toast.makeText(this, "PIN mos kelmadi. Qaytadan urinib ko'ring.", Toast.LENGTH_SHORT).show()
                    updateStatusText()
                }
            }
        } else {
            if (pinManager.verifyPin(enteredPin)) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                shakeDots()
                Toast.makeText(this, "Noto'g'ri PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }

    private fun shakeDots() {
        val container = findViewById<android.view.ViewGroup>(R.id.pin_dots_container)
        container.animate()
            .translationX(20f)
            .setDuration(50)
            .withEndAction {
                container.animate()
                    .translationX(-20f)
                    .setDuration(50)
                    .withEndAction {
                        container.animate()
                            .translationX(20f)
                            .setDuration(50)
                            .withEndAction {
                                container.animate()
                                    .translationX(0f)
                                    .setDuration(50)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
        vibrate()
    }
}
