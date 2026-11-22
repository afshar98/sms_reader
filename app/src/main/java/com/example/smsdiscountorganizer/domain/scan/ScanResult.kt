package com.example.smsdiscountorganizer.domain.scan

sealed interface ScanResult {
    data class Success(
        val messagesScanned: Int,
        val offersUpserted: Int,
        val completedAtMillis: Long,
    ) : ScanResult

    data class Failure(val reason: Reason) : ScanResult

    enum class Reason {
        InboxReadFailed,
        OfferExtractionFailed,
        PersistenceFailed,
    }
}
