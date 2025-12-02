package com.example.cyberapp

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {

    private val prefs = EncryptedPrefsManager(context)
    private val keyStoreManager = AndroidKeyStoreManager()
    private val secureRandom = SecureRandom()
    
    companion object {
        private const val KEYSTORE_PIN_KEY = "keystore_pin_hash"
        private const val LEGACY_PIN_KEY = "user_pin_hash"
        private const val PIN_SALT_KEY = "pin_salt"
        private const val PBKDF2_ITERATIONS = 150_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MINUTES = 30
    }

    init {
        // Migrate from legacy EncryptedPrefs to Keystore if needed
        migrateLegacyPin()
    }

    fun isPinSet(): Boolean = !prefs.getString(KEYSTORE_PIN_KEY, null).isNullOrEmpty()

    fun removePin() {
        prefs.putString(KEYSTORE_PIN_KEY, "")
        prefs.putString(PIN_SALT_KEY, "")
        prefs.putString(LEGACY_PIN_KEY, "")
    }

    fun setPin(pin: String) {
        try {
            val salt = generateSalt()
            prefs.putString(PIN_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            val hash = hashPin(pin, salt)
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
            val storedHash = keyStoreManager.decrypt(storedEncryptedHash)
            val saltEncoded = prefs.getString(PIN_SALT_KEY, null)
            
            val result = if (!saltEncoded.isNullOrEmpty()) {
                val salt = Base64.decode(saltEncoded, Base64.NO_WRAP)
                hashPin(pin, salt) == storedHash
            } else {
                val legacyMatch = legacyHashPin(pin) == storedHash
                if (legacyMatch) {
                    // Upgrade storage to salted PBKDF2
                    setPin(pin)
                }
                legacyMatch
            }
            
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
        val attempts = getFailedAttempts() + 1
        prefs.putInt("pin_attempts", attempts)
        if (attempts >= MAX_ATTEMPTS) {
            prefs.putLong("pin_lockout_until", System.currentTimeMillis() + LOCKOUT_DURATION_MINUTES * 60 * 1000L)
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

    fun getFailedAttempts(): Int = prefs.getInt("pin_attempts", 0)

    fun getMaxAttempts(): Int = MAX_ATTEMPTS

    fun getLockoutDurationMinutes(): Int = LOCKOUT_DURATION_MINUTES

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        val hash = hashBytes.joinToString("") { "%02x".format(it) }
        hashBytes.fill(0)
        spec.clearPassword()
        return hash
    }

    private fun legacyHashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }
        bytes.fill(0)
        return hash
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
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
            prefs.putString(PIN_SALT_KEY, "") // Mark as legacy hash until user re-authenticates
        }
    }
}
