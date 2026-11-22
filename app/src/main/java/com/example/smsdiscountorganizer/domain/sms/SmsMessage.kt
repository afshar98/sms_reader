package com.example.smsdiscountorganizer.domain.sms

/** In-memory Inbox row. The body must never be persisted or logged. */
data class SmsMessage(
    val id: Long,
    val address: String?,
    val body: String?,
    val dateMillis: Long,
)
