package com.example.cyberapp

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class NetworkStatsHelper(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    data class NetworkUsage(
        val rxBytes: Long,  // Received bytes
        val txBytes: Long,  // Transmitted bytes
        val timestamp: Long
    )

    /**
     * Get total network usage for the current app in the last time period
     * @param durationMillis Time period to check (default: last 1 hour)
     * @return NetworkUsage data or null if unavailable
     */
    fun getAppNetworkUsage(durationMillis: Long = 3600000): NetworkUsage? {
        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - durationMillis
            val uid = android.os.Process.myUid()

            var totalRx = 0L
            var totalTx = 0L

            // Query WiFi usage
            val wifiUsage = queryNetworkUsage(
                ConnectivityManager.TYPE_WIFI,
                startTime,
                endTime,
                uid
            )
            totalRx += wifiUsage.first
            totalTx += wifiUsage.second

            // Query Mobile data usage
            val mobileUsage = queryNetworkUsage(
                ConnectivityManager.TYPE_MOBILE,
                startTime,
                endTime,
                uid
            )
            totalRx += mobileUsage.first
            totalTx += mobileUsage.second

            return NetworkUsage(totalRx, totalTx, endTime)
        } catch (e: Exception) {
            android.util.Log.e("NetworkStatsHelper", "Error getting network usage: ${e.message}")
            return null
        }
    }

    private fun queryNetworkUsage(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        uid: Int
    ): Pair<Long, Long> {
        var rxBytes = 0L
        var txBytes = 0L

        try {
            val subscriberId = if (networkType == ConnectivityManager.TYPE_MOBILE) {
                getSubscriberId()
            } else {
                null
            }

            val networkStats = if (networkType == ConnectivityManager.TYPE_MOBILE && subscriberId != null) {
                networkStatsManager.querySummary(
                    networkType,
                    subscriberId,
                    startTime,
                    endTime
                )
            } else {
                networkStatsManager.querySummary(
                    networkType,
                    null,
                    startTime,
                    endTime
                )
            }

            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                if (bucket.uid == uid) {
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
            }
            networkStats.close()
        } catch (e: Exception) {
            android.util.Log.e("NetworkStatsHelper", "Error querying $networkType: ${e.message}")
        }

        return Pair(rxBytes, txBytes)
    }

    @android.annotation.SuppressLint("MissingPermission", "HardwareIds")
    private fun getSubscriberId(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            telephonyManager.subscriberId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detect if current network usage is anomalous
     * @param currentUsage Current network usage
     * @param baselineRx Baseline received bytes (from user profile)
     * @param baselineTx Baseline transmitted bytes (from user profile)
     * @param threshold Multiplier for anomaly detection (default: 3x baseline)
     * @return true if usage is anomalous
     */
    fun isAnomalousUsage(
        currentUsage: NetworkUsage,
        baselineRx: Long,
        baselineTx: Long,
        threshold: Float = 3.0f
    ): Boolean {
        if (baselineRx == 0L && baselineTx == 0L) {
            return false // No baseline yet
        }

        val rxAnomaly = currentUsage.rxBytes > (baselineRx * threshold)
        val txAnomaly = currentUsage.txBytes > (baselineTx * threshold)

        return rxAnomaly || txAnomaly
    }

    /**
     * Format bytes to human-readable string
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
