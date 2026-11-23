package com.example.smsdiscountorganizer.domain.merchant

/** Derived sender evidence only. Sender IDs and SMS bodies are deliberately absent. */
data class SenderMerchantProfile(
    val senderFingerprint: String,
    val merchantSupport: Map<String, Int>,
    val totalSupportingMessages: Int,
    val isConflicted: Boolean,
    val isSharedGateway: Boolean,
    val confidence: Double,
    val resolvedMerchantName: String?,
)
