package com.example.smsdiscountorganizer.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanStateDao {
    @Query("SELECT * FROM scan_state WHERE scanKey = 'inbox' LIMIT 1")
    fun observe(): Flow<ScanStateEntity?>

    @Query("SELECT * FROM scan_state WHERE scanKey = 'inbox' LIMIT 1")
    suspend fun get(): ScanStateEntity?

    @Upsert
    suspend fun upsert(state: ScanStateEntity)

    @Query("DELETE FROM scan_state")
    suspend fun clear()
}
