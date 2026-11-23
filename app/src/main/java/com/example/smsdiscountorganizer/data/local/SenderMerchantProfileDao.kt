package com.example.smsdiscountorganizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderMerchantProfileDao {
    @Query("SELECT * FROM sender_merchant_profiles ORDER BY senderFingerprint, merchantName")
    suspend fun getAll(): List<SenderMerchantProfileEntity>

    @Query("SELECT * FROM sender_merchant_profiles ORDER BY senderFingerprint, merchantName")
    fun observeAll(): Flow<List<SenderMerchantProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<SenderMerchantProfileEntity>)

    @Query("DELETE FROM sender_merchant_profiles")
    suspend fun clear()
}
