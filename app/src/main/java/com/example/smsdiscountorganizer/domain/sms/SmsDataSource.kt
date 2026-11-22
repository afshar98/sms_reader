package com.example.smsdiscountorganizer.domain.sms

interface SmsDataSource {
    suspend fun readInbox(): List<SmsMessage>
}
