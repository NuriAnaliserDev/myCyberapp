package com.example.cyberapp

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileOutputStream

/**
 * Encrypted File Logger
 * Provides secure storage for log files using EncryptedFile API
 * Uses AES256-GCM-HKDF encryption
 */
class EncryptedLogger(private val context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    /**
     * Write log entry to encrypted file
     * @param filename Name of the log file
     * @param data Log data to write
     * @param append If true, append to existing file; if false, overwrite
     */
    fun writeLog(filename: String, data: String, append: Boolean = true) {
        try {
            val file = File(context.filesDir, filename)
            
            // Read existing content if appending
            val existingContent = if (append && file.exists()) {
                readLog(filename)
            } else {
                ""
            }
            
            // Combine existing and new content
            val newContent = if (existingContent.isNotEmpty()) {
                existingContent + data
            } else {
                data
            }
            
            // Write encrypted
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            
            encryptedFile.openFileOutput().use { output ->
                output.write(newContent.toByteArray())
            }
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Error writing log: ${e.message}")
        }
    }

    /**
     * Append log entry to encrypted file
     * @param filename Name of the log file
     * @param data Log data to append
     */
    fun appendLog(filename: String, data: String) {
        writeLog(filename, data, append = true)
    }

    /**
     * Read log from encrypted file
     * @param filename Name of the log file
     * @return Decrypted log content
     */
    fun readLog(filename: String): String {
        return try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) {
                return ""
            }
            
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            
            encryptedFile.openFileInput().bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Error reading log: ${e.message}")
            ""
        }
    }

    /**
     * Check if log file exists
     */
    fun logExists(filename: String): Boolean {
        return File(context.filesDir, filename).exists()
    }

    /**
     * Get log file size
     */
    fun getLogSize(filename: String): Long {
        val file = File(context.filesDir, filename)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Delete log file
     */
    fun deleteLog(filename: String): Boolean {
        return try {
            val file = File(context.filesDir, filename)
            file.delete()
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Error deleting log: ${e.message}")
            false
        }
    }

    /**
     * Migrate plain text log to encrypted format
     * @param filename Name of the log file
     * @return true if migration successful
     */
    fun migratePlainTextLog(filename: String): Boolean {
        return try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) {
                return false
            }
            
            // Read plain text content
            val plainContent = file.readText()
            
            // Write as encrypted
            writeLog(filename, plainContent, append = false)
            
            android.util.Log.i("EncryptedLogger", "Migrated $filename to encrypted format")
            true
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Error migrating log: ${e.message}")
            false
        }
    }
}
