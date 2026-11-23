package com.example.smsdiscountorganizer.domain.scan

/** Privacy-safe scan cache entry. Fingerprints and derived flags only; never SMS text or address. */
data class ProcessedSmsRecord(
    val smsId: Long,
    val sourceDateMillis: Long,
    val contentFingerprint: String,
    val pipelineVersion: Int,
    val producedOffer: Boolean,
)
