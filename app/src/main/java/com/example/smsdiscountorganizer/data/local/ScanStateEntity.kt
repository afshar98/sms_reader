package com.example.smsdiscountorganizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val scanKey: String,
    val lastScannedSmsDateMillis: Long,
    val lastScannedSmsId: Long,
    val lastSuccessfulScanAtMillis: Long,
)
