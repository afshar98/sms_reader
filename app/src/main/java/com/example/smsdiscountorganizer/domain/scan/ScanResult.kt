package com.example.smsdiscountorganizer.domain.scan

sealed interface ScanResult {
    data class Success(
        val messagesScanned: Int,
        val offersUpserted: Int,
        val completedAtMillis: Long,
        val malformedMessages: Int = 0,
        val messagesUnchanged: Int = 0,
    ) : ScanResult

    data class Failure(val reason: Reason) : ScanResult

    enum class Reason {
        InboxReadFailed,
        PermissionDenied,
        OfferExtractionFailed,
        PersistenceFailed,
        InvalidDateRange,
    }
}
