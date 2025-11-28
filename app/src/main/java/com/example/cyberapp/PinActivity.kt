package com.example.cyberapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.OvershootInterpolator
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
        if (currentPin.length < 8) {
            currentPin.append(digit)
            updateDots()
            vibrate()
            updateDots()
            
            // Auto-submit if 8 digits, or user can press enter (need to add enter button if not present)
            // For now, keeping auto-submit at 4 for backward compat, but allowing up to 8
            if (currentPin.length == 4) handlePinComplete() // TODO: Add "OK" button for longer PINs
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
            if (pinManager.isLockedOut()) {
                val remainingTime = pinManager.getRemainingLockoutTime() / 1000 / 60
                Toast.makeText(this, "Juda ko'p urinishlar! $remainingTime daqiqadan so'ng urinib ko'ring.", Toast.LENGTH_LONG).show()
                shakeDots()
                currentPin.clear()
                updateDots()
                return
            }

            if (pinManager.verifyPin(enteredPin)) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                shakeDots()
                if (pinManager.isLockedOut()) {
                    Toast.makeText(this, "PIN bloklandi! 30 daqiqa kuting.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Noto'g'ri PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun shakeDots() {
        val container = findViewById<android.view.ViewGroup>(R.id.pin_dots_container)
        ValueAnimator.ofFloat(0f, 30f, -30f, 15f, -15f, 0f).apply {
            duration = 400
            interpolator = OvershootInterpolator()
            addUpdateListener { animator ->
                container.translationX = animator.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    container.translationX = 0f
                }
            })
            start()
        }
        vibrate()
    }
}
