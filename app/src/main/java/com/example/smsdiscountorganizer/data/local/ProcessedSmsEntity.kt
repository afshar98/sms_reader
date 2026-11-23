package com.example.smsdiscountorganizer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "processed_sms", indices = [Index(value = ["sourceDateMillis"])])
data class ProcessedSmsEntity(
    @PrimaryKey val smsId: Long,
    val sourceDateMillis: Long,
    val contentFingerprint: String,
    val pipelineVersion: Int,
    val producedOffer: Boolean,
)
