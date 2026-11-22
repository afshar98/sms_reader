package com.example.smsdiscountorganizer.domain.sms

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSmsInboxTest {
    private val scannedAt = Instant.parse("2026-11-22T10:00:00Z")

    @Test
    fun scanReturnsOnlyCountAndTimestamp() = runBlocking {
        val source = FakeSmsDataSource(
            messages = listOf(
                SmsMessage(1L, "+10000000000", "private message body", 100L),
                SmsMessage(2L, "+10000000001", "another private body", 200L),
            ),
        )
        val scanner = ScanSmsInbox(source, fixedClock())

        val result = scanner.scan()

        assertEquals(SmsInboxScanResult.Success(messageCount = 2, scannedAt = scannedAt), result)
        assertEquals(1, source.readCount)
    }

    @Test
    fun scanMapsPermissionFailureWithoutExposingExceptionText() = runBlocking {
        val source = FakeSmsDataSource(failure = SecurityException("private message body"))
        val scanner = ScanSmsInbox(source, fixedClock())

        val result = scanner.scan()

        assertEquals(
            SmsInboxScanResult.Failure(SmsReadError.PermissionDenied),
            result,
        )
    }

    @Test
    fun scanMapsOtherReadFailuresToGenericError() = runBlocking {
        val source = FakeSmsDataSource(failure = IllegalStateException("private message body"))
        val scanner = ScanSmsInbox(source, fixedClock())

        assertEquals(
            SmsInboxScanResult.Failure(SmsReadError.ReadFailed),
            scanner.scan(),
        )
    }

    private fun fixedClock(): Clock = Clock.fixed(scannedAt, ZoneOffset.UTC)
}
