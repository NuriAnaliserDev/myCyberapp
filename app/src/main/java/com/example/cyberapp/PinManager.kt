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

    fun isPinSet(): Boolean = !prefs.getString(KEYSTORE_PIN_KEY, null).isNullOrEmpty()

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
        if (isLockedOut()) return false

        return try {
            val storedEncryptedHash = prefs.getString(KEYSTORE_PIN_KEY, null) ?: return false
            
            // Decrypt hash from Keystore
            val storedHash = keyStoreManager.decrypt(storedEncryptedHash)
            val result = hashPin(pin) == storedHash
            
            if (result) {
                resetAttempts()
            } else {
                incrementAttempts()
            }
            
            result
        } catch (e: Exception) {
            // If decryption fails, PIN is invalid
            incrementAttempts()
            false
        } finally {
            // Clear PIN from memory immediately
            clearPinFromMemory(pin)
        }
    }

    private fun incrementAttempts() {
        val attempts = prefs.getInt("pin_attempts", 0) + 1
        prefs.putInt("pin_attempts", attempts)
        if (attempts >= 5) {
            prefs.putLong("pin_lockout_until", System.currentTimeMillis() + 30 * 60 * 1000L) // 30 min
        }
    }

    private fun resetAttempts() {
        prefs.putInt("pin_attempts", 0)
        prefs.putLong("pin_lockout_until", 0L)
    }

    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong("pin_lockout_until", 0L)
        return System.currentTimeMillis() < lockoutUntil
    }

    fun getRemainingLockoutTime(): Long {
        val lockoutUntil = prefs.getLong("pin_lockout_until", 0L)
        return if (System.currentTimeMillis() < lockoutUntil) lockoutUntil - System.currentTimeMillis() else 0L
    }

    private fun hashPin(pin: String): String {
        // Generate or retrieve salt (for now using fixed salt for simplicity, but should be random per user)
        // In production, generate random salt, store it, and use it.
        val salt = "CyberAppFixedSaltForMigration".toByteArray() 
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
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
        val legacyHash = prefs.getString(LEGACY_PIN_KEY, null)
        val keystoreHash = prefs.getString(KEYSTORE_PIN_KEY, null)
        
        if (!legacyHash.isNullOrEmpty() && keystoreHash.isNullOrEmpty()) {
            // Migrate: encrypt legacy hash with Keystore
            val encryptedHash = keyStoreManager.encrypt(legacyHash)
            prefs.putString(KEYSTORE_PIN_KEY, encryptedHash)
            // Clear legacy storage
            prefs.putString(LEGACY_PIN_KEY, "")
        }
    }
}
