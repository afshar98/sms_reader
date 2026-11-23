package com.example.smsdiscountorganizer.domain.sms

import kotlinx.coroutines.flow.Flow
import com.example.smsdiscountorganizer.domain.scan.SmsDateRange

interface SmsDataSource {
    /** Emits Inbox rows in ascending SMS ID order without materializing the Inbox. */
    fun readInbox(after: SmsCursor? = null): Flow<SmsMessage>

    /** Reads only rows whose provider timestamp falls within the half-open range, when supplied. */
    fun readInbox(after: SmsCursor?, dateRange: SmsDateRange?): Flow<SmsMessage> = readInbox(after)

    /** Reads a single Inbox row by ID. Implementations must not retain its body. */
    suspend fun readMessageById(id: Long): SmsMessage?
}
