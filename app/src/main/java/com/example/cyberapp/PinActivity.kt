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
    private lateinit var pinLengthIndicator: TextView
    private lateinit var helperText: TextView

    companion object {
        private const val PIN_LENGTH = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        pinManager = PinManager(this)
        isSetupMode = intent.getBooleanExtra("SETUP_MODE", false)

        setupViews()
        updateStatusText()
        updateLengthIndicator()
        updateHelperText()
    }

    private fun setupViews() {
        val dotsContainer = findViewById<android.view.ViewGroup>(R.id.pin_dots_container)
        for (i in 0 until dotsContainer.childCount) {
            dots.add(dotsContainer.getChildAt(i) as ImageView)
        }
        pinLengthIndicator = findViewById(R.id.pin_length_indicator)
        helperText = findViewById(R.id.pin_helper_text)

        val keypadContainer = findViewById<android.widget.LinearLayout>(R.id.keypad_container)
        for (i in 0 until keypadContainer.childCount) {
            val row = keypadContainer.getChildAt(i) as? android.widget.LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val view = row.getChildAt(j)
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
    }

    private fun updateStatusText() {
        val statusText = findViewById<TextView>(R.id.pin_status)
        if (isSetupMode) {
            statusText.text = if (firstPinAttempt.isEmpty()) "Yangi PIN o'rnating" else "PINni tasdiqlang"
        } else {
            statusText.text = "PIN kodni kiriting"
        }
        updateHelperText()
    }

    private fun onDigitClick(digit: String) {
        if (currentPin.length >= PIN_LENGTH) {
            return
        }
        currentPin.append(digit)
        updateDots()
        vibrate()
        if (currentPin.length == PIN_LENGTH) {
            handlePinComplete()
        }
    }

    private fun onDeleteClick() {
        if (currentPin.isNotEmpty()) {
            currentPin.deleteCharAt(currentPin.length - 1)
            updateDots()
            vibrate()
            updateHelperText()
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
        updateLengthIndicator()
    }

    private fun handlePinComplete() {
        val enteredPin = currentPin.toString()
        currentPin.clear()
        updateDots()
        updateLengthIndicator()

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
                    updateHelperText(getString(R.string.pin_error_mismatch))
                }
            }
        } else {
            if (pinManager.isLockedOut()) {
                val remainingTime = pinManager.getRemainingLockoutTime() / 1000 / 60
                Toast.makeText(this, "Juda ko'p urinishlar! $remainingTime daqiqadan so'ng urinib ko'ring.", Toast.LENGTH_LONG).show()
                shakeDots()
                currentPin.clear()
                updateDots()
                updateLengthIndicator()
                updateHelperText(getString(R.string.pin_error_locked, pinManager.getLockoutDurationMinutes()))
                return
            }

            if (pinManager.verifyPin(enteredPin)) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                shakeDots()
                if (pinManager.isLockedOut()) {
                    Toast.makeText(this, "PIN bloklandi! 30 daqiqa kuting.", Toast.LENGTH_LONG).show()
                    updateHelperText(getString(R.string.pin_error_locked, pinManager.getLockoutDurationMinutes()))
                } else {
                    Toast.makeText(this, "Noto'g'ri PIN", Toast.LENGTH_SHORT).show()
                    val attemptsLeft = (pinManager.getMaxAttempts() - pinManager.getFailedAttempts()).coerceAtLeast(0)
                    updateHelperText(getString(R.string.pin_error_incorrect, attemptsLeft))
                }
                updateLengthIndicator()
            }
        }
    }

    private fun updateLengthIndicator() {
        if (::pinLengthIndicator.isInitialized) {
            pinLengthIndicator.text = "${currentPin.length} / $PIN_LENGTH"
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

    private fun updateHelperText(message: String? = null) {
        if (!::helperText.isInitialized) return
        helperText.text = message ?: if (isSetupMode) {
            if (firstPinAttempt.isEmpty()) getString(R.string.pin_helper_secure) else getString(R.string.pin_helper_confirm)
        } else {
            getString(R.string.pin_helper_unlock)
        }
    }
}
