package com.example.cyberapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScanHistoryEntity>>

    @Insert
    suspend fun insert(history: ScanHistoryEntity)

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()
}
