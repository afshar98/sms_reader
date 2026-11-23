package com.example.smsdiscountorganizer.domain.sms

class FakeSmsDataSource(
    private val messages: List<SmsMessage> = emptyList(),
    private val failure: Exception? = null,
) : SmsDataSource {
    var readCount: Int = 0
        private set

    override fun readInbox(after: SmsCursor?): kotlinx.coroutines.flow.Flow<SmsMessage> =
        kotlinx.coroutines.flow.flow {
            readCount += 1
            failure?.let { throw it }
            messages.asSequence()
                .filter { after == null || it.cursor > after }
                .sortedBy { it.id }
                .forEach { emit(it) }
        }

    override suspend fun readMessageById(id: Long): SmsMessage? {
        failure?.let { throw it }
        return messages.firstOrNull { it.id == id }
    }
}
