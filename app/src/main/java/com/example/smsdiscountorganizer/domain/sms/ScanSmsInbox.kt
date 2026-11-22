package com.example.smsdiscountorganizer.domain.sms

import java.time.Clock
import java.util.concurrent.CancellationException
import javax.inject.Inject

class ScanSmsInbox @Inject constructor(
    private val dataSource: SmsDataSource,
    private val clock: Clock,
) {
    suspend fun scan(): SmsInboxScanResult = try {
        val messageCount = dataSource.readInbox().size
        SmsInboxScanResult.Success(messageCount = messageCount, scannedAt = clock.instant())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        SmsInboxScanResult.Failure(SmsReadError.PermissionDenied)
    } catch (_: SmsInboxUnavailableException) {
        SmsInboxScanResult.Failure(SmsReadError.InboxUnavailable)
    } catch (_: Exception) {
        SmsInboxScanResult.Failure(SmsReadError.ReadFailed)
    }
}
