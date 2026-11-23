package com.example.smsdiscountorganizer.data.sms

import com.example.smsdiscountorganizer.domain.scan.SmsDateRange
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxQueryBoundsTest {
    @Test
    fun selectedRangeIsPushedIntoProviderSelectionWithHalfOpenDates() {
        val query = inboxQueryBounds(SmsCursor(90L, 17L), SmsDateRange(100L, 200L))

        assertEquals("_id > ? AND date >= ? AND date < ?", query.selection)
        assertArrayEquals(arrayOf("17", "100", "200"), query.selectionArgs)
    }

    @Test
    fun allMessagesRangeDoesNotAddDatePredicates() {
        val query = inboxQueryBounds(null, null)

        assertNull(query.selection)
        assertNull(query.selectionArgs)
    }
}
