package com.example.smsdiscountorganizer.domain.sms

interface SmsDataSource {
    suspend fun readInbox(after: SmsCursor? = null): List<SmsMessage>
}
