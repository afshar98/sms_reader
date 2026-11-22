package com.example.smsdiscountorganizer.domain.sms

import java.time.Instant

sealed interface SmsInboxScanResult {
    data class Success(
        val messageCount: Int,
        val scannedAt: Instant,
    ) : SmsInboxScanResult

    data class Failure(val error: SmsReadError) : SmsInboxScanResult
}

enum class SmsReadError {
    PermissionDenied,
    InboxUnavailable,
    ReadFailed,
}

class SmsInboxUnavailableException : IllegalStateException()
