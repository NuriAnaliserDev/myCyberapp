package com.example.cyberapp.utils

import java.security.MessageDigest

object HashUtils {
    fun getSha256(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
