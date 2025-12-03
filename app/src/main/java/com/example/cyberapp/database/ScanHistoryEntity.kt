package com.example.cyberapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val verdict: String, // "safe" or "dangerous"
    val timestamp: Long
)
