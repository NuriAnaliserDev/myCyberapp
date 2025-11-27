package com.example.cyberapp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted SharedPreferences Manager
 * Provides secure storage for app settings and user data
 * Uses AES256-GCM encryption via Android Security Crypto library
 */
class EncryptedPrefsManager(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "CyberAppPrefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // String operations
    fun getString(key: String, defaultValue: String?): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    // Int operations
    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    // Long operations
    fun getLong(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    // Float operations
    fun getFloat(key: String, defaultValue: Float): Float {
        return sharedPreferences.getFloat(key, defaultValue)
    }

    fun putFloat(key: String, value: Float) {
        sharedPreferences.edit().putFloat(key, value).apply()
    }

    // Boolean operations
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    // StringSet operations
    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        return sharedPreferences.getStringSet(key, defaultValue)
    }

    fun putStringSet(key: String, value: Set<String>) {
        sharedPreferences.edit().putStringSet(key, value).apply()
    }

    // Check if key exists
    fun contains(key: String): Boolean {
        return sharedPreferences.contains(key)
    }

    // Remove key
    fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    // Clear all
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    // Get editor for batch operations
    fun edit(): SharedPreferences.Editor {
        return sharedPreferences.edit()
    }

    // Get all keys
    fun getAll(): Map<String, *> {
        return sharedPreferences.all
    }
}
