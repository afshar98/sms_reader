package com.example.smsdiscountorganizer.domain.sms

class FakeSmsDataSource(
    private val messages: List<SmsMessage> = emptyList(),
    private val failure: Exception? = null,
) : SmsDataSource {
    var readCount: Int = 0
        private set

    override suspend fun readInbox(after: SmsCursor?): List<SmsMessage> {
        readCount += 1
        failure?.let { throw it }
        return messages.filter { after == null || it.cursor > after }
    }
}
