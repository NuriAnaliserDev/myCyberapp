package com.example.cyberapp.utils

import android.Manifest

object PermissionUtils {
    val DANGEROUS_PERMISSIONS = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.BIND_ACCESSIBILITY_SERVICE
    )

    fun isDangerous(permission: String): Boolean {
        return DANGEROUS_PERMISSIONS.contains(permission)
    }
}
