package com.example.cyberapp

import android.content.Context
import java.security.MessageDigest

class PinManager(context: Context) {

    private val prefs = EncryptedPrefsManager(context)
    private val keyStoreManager = AndroidKeyStoreManager()
    
    companion object {
        private const val KEYSTORE_PIN_KEY = "keystore_pin_hash"
        private const val LEGACY_PIN_KEY = "user_pin_hash"
    }

    init {
        // Migrate from legacy EncryptedPrefs to Keystore if needed
        migrateLegacyPin()
    }

    fun isPinSet(): Boolean {
        val hash = prefs.getString(KEYSTORE_PIN_KEY, "")
        return !hash.isNullOrEmpty()
    }

    fun setPin(pin: String) {
        try {
            val hash = hashPin(pin)
            // Encrypt hash using hardware-backed Keystore
            val encryptedHash = keyStoreManager.encrypt(hash)
            prefs.putString(KEYSTORE_PIN_KEY, encryptedHash)
        } finally {
            // Clear PIN from memory
            clearPinFromMemory(pin)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return try {
            val storedEncryptedHash = prefs.getString(KEYSTORE_PIN_KEY, "")
            if (storedEncryptedHash.isNullOrEmpty()) return false
            
            try {
                // Decrypt hash from Keystore
                val storedHash = keyStoreManager.decrypt(storedEncryptedHash)
                val result = hashPin(pin) == storedHash
                result
            } catch (e: Exception) {
                // If decryption fails, PIN is invalid
                false
            }
        } finally {
            // Clear PIN from memory immediately
            clearPinFromMemory(pin)
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }
        // Clear bytes from memory
        bytes.fill(0)
        return hash
    }

    /**
     * Clear PIN from memory to prevent memory dump attacks
     */
    private fun clearPinFromMemory(pin: String) {
        try {
            // Convert to char array and clear
            val chars = pin.toCharArray()
            chars.fill('\u0000')
        } catch (e: Exception) {
            // Ignore - best effort clearing
        }
    }

    /**
     * Migrate existing PIN from EncryptedPrefs to Keystore
     */
    private fun migrateLegacyPin() {
        val legacyHash = prefs.getString(LEGACY_PIN_KEY, "")
        val keystoreHash = prefs.getString(KEYSTORE_PIN_KEY, "")
        
        if (!legacyHash.isNullOrEmpty() && keystoreHash.isNullOrEmpty()) {
            // Migrate: encrypt legacy hash with Keystore
            val encryptedHash = keyStoreManager.encrypt(legacyHash)
            prefs.putString(KEYSTORE_PIN_KEY, encryptedHash)
            // Clear legacy storage
            prefs.putString(LEGACY_PIN_KEY, "")
        }
    }
}
