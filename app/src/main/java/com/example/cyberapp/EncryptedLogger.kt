package com.example.cyberapp

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import kotlin.text.Charsets

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
        val file = File(context.filesDir, filename)
        val encryptedFile = buildEncryptedFile(file)
        val tempFile = File.createTempFile(tempPrefix(file.nameWithoutExtension), ".tmp", context.cacheDir)

        try {
            if (append && file.exists()) {
                encryptedFile.openFileInput().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            tempFile.appendText(data, Charsets.UTF_8)

            encryptedFile.openFileOutput().use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("EncryptedLogger", "Error writing log: ${e.message}")
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                Log.w("EncryptedLogger", "Failed to delete temp log buffer ${tempFile.name}")
            }
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
            
            buildEncryptedFile(file).openFileInput().bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Error reading log: ${e.message}")
            try {
                val file = File(context.filesDir, filename)
                if (file.exists()) file.delete()
            } catch (ignored: Exception) {}
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
        val originalFile = File(context.filesDir, filename)
        if (!originalFile.exists()) {
            return false
        }

        val tempPlain = File.createTempFile(tempPrefix("${originalFile.nameWithoutExtension}_plain"), ".tmp", context.cacheDir)

        return try {
            val migrationSource = if (originalFile.renameTo(tempPlain)) {
                tempPlain
            } else {
                originalFile.copyTo(tempPlain, overwrite = true)
            }

            val encryptedFile = buildEncryptedFile(originalFile)
            migrationSource.inputStream().use { input ->
                encryptedFile.openFileOutput().use { output ->
                    input.copyTo(output)
                }
            }

            if (!migrationSource.delete()) {
                android.util.Log.w("EncryptedLogger", "Failed to delete temporary plain log ${migrationSource.name}")
            }

            android.util.Log.i("EncryptedLogger", "Migrated $filename to encrypted format")
            true
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Error migrating log: ${e.message}")
            false
        } finally {
            if (tempPlain.exists() && !tempPlain.delete()) {
                android.util.Log.w("EncryptedLogger", "Failed to delete migration temp file ${tempPlain.name}")
            }
        }
    }

    private fun buildEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun tempPrefix(raw: String): String {
        val sanitized = raw.ifEmpty { "log" }.replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (sanitized.length >= 3) sanitized else (sanitized + "___").take(3)
    }
}
