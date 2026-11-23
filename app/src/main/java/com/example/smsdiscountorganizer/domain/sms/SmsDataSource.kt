package com.example.smsdiscountorganizer.domain.sms

import kotlinx.coroutines.flow.Flow

interface SmsDataSource {
    /** Emits Inbox rows in ascending SMS ID order without materializing the Inbox. */
    fun readInbox(after: SmsCursor? = null): Flow<SmsMessage>

    /** Reads a single Inbox row by ID. Implementations must not retain its body. */
    suspend fun readMessageById(id: Long): SmsMessage?
}
