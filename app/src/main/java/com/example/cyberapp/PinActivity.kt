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
    private lateinit var biometricAuthManager: BiometricAuthManager
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
        biometricAuthManager = BiometricAuthManager(this)
        
        // Check request type from SettingsActivity
        val requestType = intent.getStringExtra("REQUEST_TYPE")
        when (requestType) {
            "CHANGE_PIN" -> {
                // First verify old PIN, then setup new one
                isSetupMode = false // Start in verification mode
            }
            "SET_PIN" -> {
                isSetupMode = true
            }
            "REMOVE_PIN" -> {
                // Verify PIN first, then remove
                isSetupMode = false
            }
            else -> {
                isSetupMode = intent.getBooleanExtra("SETUP_MODE", false)
            }
        }

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
                    view.setOnClickListener { startBiometricAuth() }
                }
            }
        }

        // Check if biometrics are available, if not hide the button
        val fingerprintBtn = findViewById<ImageButton>(R.id.btn_fingerprint)
        if (!biometricAuthManager.canAuthenticate()) {
            fingerprintBtn.visibility = android.view.View.GONE
        } else if (!isSetupMode) {
            // Auto-start biometrics if not in setup mode
            startBiometricAuth()
        }
    }

    private fun startBiometricAuth() {
        biometricAuthManager.authenticate(
            onSuccess = {
                setResult(Activity.RESULT_OK)
                finish()
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            },
            onFailed = {
                Toast.makeText(this, getString(R.string.biometric_auth_failed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateStatusText() {
        val statusText = findViewById<TextView>(R.id.pin_status)
        if (isSetupMode) {
            statusText.text = if (firstPinAttempt.isEmpty()) getString(R.string.pin_setup_new) else getString(R.string.pin_setup_confirm)
        } else {
            statusText.text = getString(R.string.pin_enter_code)
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
                    Toast.makeText(this, getString(R.string.pin_set_success), Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    firstPinAttempt = ""
                    shakeDots()
                    Toast.makeText(this, getString(R.string.pin_mismatch_retry), Toast.LENGTH_SHORT).show()
                    updateStatusText()
                    updateHelperText(getString(R.string.pin_error_mismatch))
                }
            }
        } else {
            if (pinManager.isLockedOut()) {
                val remainingTime = pinManager.getRemainingLockoutTime() / 1000 / 60
                Toast.makeText(this, getString(R.string.pin_too_many_attempts, remainingTime), Toast.LENGTH_LONG).show()
                shakeDots()
                currentPin.clear()
                updateDots()
                updateLengthIndicator()
                updateHelperText(getString(R.string.pin_error_locked, pinManager.getLockoutDurationMinutes()))
                return
            }

            val requestType = intent.getStringExtra("REQUEST_TYPE")
            if (pinManager.verifyPin(enteredPin)) {
                when (requestType) {
                    "CHANGE_PIN" -> {
                        // PIN verified, now switch to setup mode for new PIN
                        isSetupMode = true
                        firstPinAttempt = ""
                        updateStatusText()
                        updateHelperText(getString(R.string.pin_helper_secure))
                        Toast.makeText(this, getString(R.string.pin_verified_now_set_new), Toast.LENGTH_SHORT).show()
                    }
                    "REMOVE_PIN" -> {
                        // PIN verified, remove it
                        pinManager.removePin()
                        val resultIntent = Intent().apply {
                            putExtra("REQUEST_TYPE", "REMOVE_PIN")
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                    else -> {
                        // Normal unlock
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            } else {
                shakeDots()
                if (pinManager.isLockedOut()) {
                    Toast.makeText(this, getString(R.string.pin_locked_wait), Toast.LENGTH_LONG).show()
                    updateHelperText(getString(R.string.pin_error_locked, pinManager.getLockoutDurationMinutes()))
                } else {
                    Toast.makeText(this, getString(R.string.pin_incorrect), Toast.LENGTH_SHORT).show()
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
