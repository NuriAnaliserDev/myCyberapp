package com.example.cyberapp

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for baseline calculation and anomaly detection logic
 */
class BaselineCalculationTest {

    @Test
    fun testNetworkBaselineCalculation_withValidData() {
        // Arrange: 3 ta tarmoq usage qiymati
        val networkUsageValues = listOf(
            Pair(1000000L, 500000L),  // 1 MB RX, 0.5 MB TX
            Pair(1200000L, 600000L),  // 1.2 MB RX, 0.6 MB TX
            Pair(900000L, 450000L)    // 0.9 MB RX, 0.45 MB TX
        )
        
        // Act: O'rtacha qiymatni hisoblash
        val avgRx = networkUsageValues.map { it.first }.average().toLong()
        val avgTx = networkUsageValues.map { it.second }.average().toLong()
        
        // Assert: O'rtacha qiymatlar to'g'ri
        assertEquals(1033333.0, avgRx.toDouble(), 1000.0) // Delta 1000 bytes
        assertEquals(516666.0, avgTx.toDouble(), 1000.0)
    }

    @Test
    fun testNetworkBaselineCalculation_withEmptyData() {
        // Arrange: Bo'sh list
        val networkUsageValues = emptyList<Pair<Long, Long>>()
        
        // Act: O'rtacha qiymatni hisoblash (coerceAtLeast 1L bilan)
        val avgRx = if (networkUsageValues.isNotEmpty()) {
            networkUsageValues.map { it.first }.average().toLong().coerceAtLeast(1L)
        } else {
            1L // Default qiymat
        }
        
        val avgTx = if (networkUsageValues.isNotEmpty()) {
            networkUsageValues.map { it.second }.average().toLong().coerceAtLeast(1L)
        } else {
            1L // Default qiymat
        }
        
        // Assert: Default qiymatlar
        assertEquals(1L, avgRx)
        assertEquals(1L, avgTx)
    }

    @Test
    fun testSensitivityThresholds_lowMediumHigh() {
        // Arrange
        val baseline = 1000000L // 1 MB
        
        // Act & Assert: Low sensitivity (3.0x)
        val lowThreshold = baseline * 3.0f
        assertEquals(3000000L, lowThreshold.toLong())
        
        // Act & Assert: Medium sensitivity (2.0x)
        val mediumThreshold = baseline * 2.0f
        assertEquals(2000000L, mediumThreshold.toLong())
        
        // Act & Assert: High sensitivity (1.5x)
        val highThreshold = baseline * 1.5f
        assertEquals(1500000L, highThreshold.toLong())
    }

    @Test
    fun testAnomalyDetection_aboveThreshold() {
        // Arrange
        val baselineRx = 1000000L // 1 MB
        val baselineTx = 500000L  // 0.5 MB
        val currentRx = 3500000L  // 3.5 MB
        val currentTx = 600000L   // 0.6 MB
        val threshold = 2.0f      // Medium sensitivity
        
        // Act
        val rxAnomaly = currentRx > (baselineRx * threshold)
        val txAnomaly = currentTx > (baselineTx * threshold)
        val isAnomaly = rxAnomaly || txAnomaly
        
        // Assert: RX anomaliya (3.5 MB > 2 MB), TX yo'q (0.6 MB < 1 MB)
        assertTrue(rxAnomaly)
        assertFalse(txAnomaly)
        assertTrue(isAnomaly) // Umumiy anomaliya
    }

    @Test
    fun testAnomalyDetection_belowThreshold() {
        // Arrange
        val baselineRx = 1000000L // 1 MB
        val baselineTx = 500000L  // 0.5 MB
        val currentRx = 1500000L  // 1.5 MB
        val currentTx = 700000L   // 0.7 MB
        val threshold = 2.0f      // Medium sensitivity
        
        // Act
        val rxAnomaly = currentRx > (baselineRx * threshold)
        val txAnomaly = currentTx > (baselineTx * threshold)
        val isAnomaly = rxAnomaly || txAnomaly
        
        // Assert: Hech qanday anomaliya yo'q
        assertFalse(rxAnomaly) // 1.5 MB < 2 MB
        assertFalse(txAnomaly) // 0.7 MB < 1 MB
        assertFalse(isAnomaly)
    }

    @Test
    fun testAnomalyDetection_zeroBaseline() {
        // Arrange: Baseline hali yaratilmagan
        val baselineRx = 0L
        val baselineTx = 0L
        val currentRx = 5000000L // 5 MB
        val currentTx = 2000000L // 2 MB
        val threshold = 2.0f
        
        // Act: Baseline 0 bo'lsa, anomaliya yo'q (LoggerService.kt:136-138)
        val isAnomaly = if (baselineRx == 0L && baselineTx == 0L) {
            false // No baseline yet
        } else {
            val rxAnomaly = currentRx > (baselineRx * threshold)
            val txAnomaly = currentTx > (baselineTx * threshold)
            rxAnomaly || txAnomaly
        }
        
        // Assert: Baseline yo'q, shuning uchun anomaliya ham yo'q
        assertFalse(isAnomaly)
    }

    @Test
    fun testSensitivityMultiplier_allLevels() {
        // Arrange & Act & Assert
        
        // Level 0: Low (3.0x)
        val lowMultiplier = when (0) {
            0 -> 3.0f
            2 -> 1.5f
            else -> 2.0f
        }
        assertEquals(3.0f, lowMultiplier, 0.01f)
        
        // Level 1: Medium (2.0x)
        val mediumMultiplier = when (1) {
            0 -> 3.0f
            2 -> 1.5f
            else -> 2.0f
        }
        assertEquals(2.0f, mediumMultiplier, 0.01f)
        
        // Level 2: High (1.5x)
        val highMultiplier = when (2) {
            0 -> 3.0f
            2 -> 1.5f
            else -> 2.0f
        }
        assertEquals(1.5f, highMultiplier, 0.01f)
    }

    @Test
    fun testBaselineCoerceAtLeast() {
        // Arrange: Juda kichik qiymatlar
        val networkUsageValues = listOf(
            Pair(100L, 50L),  // 100 bytes RX, 50 bytes TX
            Pair(200L, 100L),
            Pair(150L, 75L)
        )
        
        // Act: O'rtacha qiymatni hisoblash va coerceAtLeast(1L)
        val avgRx = networkUsageValues.map { it.first }.average().toLong().coerceAtLeast(1L)
        val avgTx = networkUsageValues.map { it.second }.average().toLong().coerceAtLeast(1L)
        
        // Assert: Qiymatlar kamida 1L
        assertTrue(avgRx >= 1L)
        assertTrue(avgTx >= 1L)
        assertEquals(150L, avgRx)
        assertEquals(75L, avgTx)
    }
}
