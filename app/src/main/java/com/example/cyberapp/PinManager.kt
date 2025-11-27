package com.example.cyberapp

import android.content.Context
import java.security.MessageDigest

class PinManager(context: Context) {

    private val prefs = EncryptedPrefsManager(context)

    fun isPinSet(): Boolean {
        return prefs.getString("user_pin_hash", "").isNotEmpty()
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.putString("user_pin_hash", hash)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("user_pin_hash", "")
        if (storedHash.isEmpty()) return false
        return hashPin(pin) == storedHash
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
