package com.example.smsdiscountorganizer.domain.sms

/** Inbox position ordered by row ID so later backdated messages are still discovered. */
data class SmsCursor(val dateMillis: Long, val smsId: Long) : Comparable<SmsCursor> {
    override fun compareTo(other: SmsCursor): Int =
        compareValuesBy(this, other, SmsCursor::smsId, SmsCursor::dateMillis)
}

val SmsMessage.cursor: SmsCursor
    get() = SmsCursor(dateMillis, id)
