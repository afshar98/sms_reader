package com.example.smsdiscountorganizer.data.local

import androidx.room.Entity
import androidx.room.Index

/** Contains only hashed sender IDs and derived merchant evidence; never SMS content. */
@Entity(
    tableName = "sender_merchant_profiles",
    primaryKeys = ["senderFingerprint", "merchantName"],
    indices = [Index("senderFingerprint")],
)
data class SenderMerchantProfileEntity(
    val senderFingerprint: String,
    val merchantName: String,
    val supportingMessages: Int,
    val totalSupportingMessages: Int,
    val isConflicted: Boolean,
    val isSharedGateway: Boolean,
    val confidence: Double,
    val resolvedMerchantName: String?,
)
