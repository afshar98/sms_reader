package com.example.smsdiscountorganizer.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY sourceDateMillis DESC, sourceSmsId DESC")
    fun observeAll(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE sourceSmsId = :sourceSmsId LIMIT 1")
    suspend fun getBySourceSmsId(sourceSmsId: Long): OfferEntity?

    @Query("UPDATE offers SET merchantCorrection = :merchantName WHERE sourceSmsId = :sourceSmsId")
    suspend fun setMerchantCorrection(sourceSmsId: Long, merchantName: String?)

    @Query("SELECT COUNT(*) FROM offers")
    suspend fun count(): Int

    @Upsert
    @Transaction
    suspend fun upsertAll(offers: List<OfferEntity>)

    @Query("DELETE FROM offers")
    suspend fun deleteAll()
}
