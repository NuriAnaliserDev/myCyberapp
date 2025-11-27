package com.example.cyberapp

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class BiometricAuthManager(private val activity: FragmentActivity) {

    private val executor: Executor = ContextCompat.getMainExecutor(activity)
    private val biometricManager = BiometricManager.from(activity)

    fun canAuthenticate(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> {
                Log.e("BiometricAuth", "Biometric authentication not available")
                false
            }
        }
    }

    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("CyberApp Xavfsizlik")
            .setSubtitle("Kirish uchun biometrik ma'lumotlardan foydalaning")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
