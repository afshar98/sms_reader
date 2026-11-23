package com.example.smsdiscountorganizer.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProcessedSmsDao {
    @Query("SELECT * FROM processed_sms WHERE sourceDateMillis >= :startInclusive AND sourceDateMillis < :endExclusive")
    suspend fun getInRange(startInclusive: Long, endExclusive: Long): List<ProcessedSmsEntity>

    @Query("SELECT * FROM processed_sms")
    suspend fun getAll(): List<ProcessedSmsEntity>

    @Query("SELECT smsId FROM processed_sms WHERE sourceDateMillis >= :startInclusive AND sourceDateMillis < :endExclusive")
    suspend fun getSmsIdsInRange(startInclusive: Long, endExclusive: Long): List<Long>

    @Query("SELECT smsId FROM processed_sms")
    suspend fun getAllSmsIds(): List<Long>

    @Upsert
    suspend fun upsertAll(records: List<ProcessedSmsEntity>)

    @Query("DELETE FROM processed_sms WHERE smsId IN (:smsIds)")
    suspend fun deleteBySmsIds(smsIds: List<Long>)

    @Query("DELETE FROM processed_sms")
    suspend fun clear()
}
