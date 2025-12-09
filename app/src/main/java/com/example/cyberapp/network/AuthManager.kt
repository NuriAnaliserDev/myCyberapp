package com.example.cyberapp.network

import android.content.Context
import android.provider.Settings
import com.example.cyberapp.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthManager {
    private const val AUTH_TOKEN_KEY = "auth_token"
    private const val USER_ID_KEY = "user_id"
    private const val DEVICE_ID_KEY = "device_id"
    private const val TOKEN_EXPIRES_AT_KEY = "token_expires_at"
    
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
    }
    
    fun getAuthToken(prefs: EncryptedPrefsManager): String? {
        return prefs.getString(AUTH_TOKEN_KEY, null)
    }
    
    fun saveAuthToken(prefs: EncryptedPrefsManager, token: String, userId: Int, deviceId: String, expiresAt: Double? = null) {
        prefs.putString(AUTH_TOKEN_KEY, token)
        prefs.putInt(USER_ID_KEY, userId)
        prefs.putString(DEVICE_ID_KEY, deviceId)
        if (expiresAt != null) {
            prefs.putLong(TOKEN_EXPIRES_AT_KEY, (expiresAt * 1000).toLong())
        } else {
            // Default: 30 days from now
            prefs.putLong(TOKEN_EXPIRES_AT_KEY, System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L))
        }
    }
    
    fun clearAuthToken(prefs: EncryptedPrefsManager) {
        prefs.remove(AUTH_TOKEN_KEY)
        prefs.putInt(USER_ID_KEY, 0)
        prefs.remove(DEVICE_ID_KEY)
        prefs.putLong(TOKEN_EXPIRES_AT_KEY, 0L)
    }
    
    fun isAuthenticated(prefs: EncryptedPrefsManager): Boolean {
        val token = getAuthToken(prefs)
        if (token.isNullOrEmpty()) return false
        
        // Check if token is expired
        val expiresAt = prefs.getLong(TOKEN_EXPIRES_AT_KEY, 0L)
        if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
            // Token expired
            return false
        }
        
        return true
    }
    
    fun isTokenExpiringSoon(prefs: EncryptedPrefsManager): Boolean {
        val expiresAt = prefs.getLong(TOKEN_EXPIRES_AT_KEY, 0L)
        if (expiresAt == 0L) return false
        
        // Check if token expires in next 3 days
        val threeDaysInMs = 3 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() >= (expiresAt - threeDaysInMs)
    }
    
    fun getTokenExpirationTime(prefs: EncryptedPrefsManager): Long {
        return prefs.getLong(TOKEN_EXPIRES_AT_KEY, 0L)
    }
    
    suspend fun authenticate(context: Context, password: String? = null): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId(context)
                val prefs = EncryptedPrefsManager(context)
                
                // Try to login first
                val loginResult = try {
                    RetrofitClient.api.login(LoginRequest(deviceId, password))
                } catch (e: Exception) {
                    null
                }
                
                if (loginResult != null) {
                    saveAuthToken(prefs, loginResult.auth_token, loginResult.user_id, loginResult.device_id, loginResult.expires_at)
                    Result.success(loginResult)
                } else {
                    // If login fails, try to register
                    val registerResult = RetrofitClient.api.register(RegisterRequest(deviceId, password))
                    saveAuthToken(prefs, registerResult.auth_token, registerResult.user_id, registerResult.device_id, registerResult.expires_at)
                    Result.success(registerResult)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun logout(context: Context): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = EncryptedPrefsManager(context)
                val token = getAuthToken(prefs)
                
                if (!token.isNullOrEmpty()) {
                    try {
                        RetrofitClient.api.logout("Bearer $token")
                    } catch (e: Exception) {
                        // Ignore logout errors
                    }
                }
                
                clearAuthToken(prefs)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Refreshes the authentication token if it's expired or expiring soon
     */
    suspend fun refreshTokenIfNeeded(context: Context): Result<AuthResponse>? {
        return withContext(Dispatchers.IO) {
            val prefs = EncryptedPrefsManager(context)
            
            // Check if token is expired or expiring soon
            if (!isAuthenticated(prefs) || isTokenExpiringSoon(prefs)) {
                // Try to refresh token
                return@withContext authenticate(context, null)
            }
            
            return@withContext null
        }
    }
}

