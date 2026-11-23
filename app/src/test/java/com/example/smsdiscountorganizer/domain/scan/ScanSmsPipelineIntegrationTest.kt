package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.domain.sms.cursor
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import com.example.smsdiscountorganizer.engine.expiration.PersianDateResolver
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import com.example.smsdiscountorganizer.engine.OfferExtractor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSmsPipelineIntegrationTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-06-01T10:00:00Z")
    private val clock = Clock.fixed(now, zone)

    @Test
    fun firstScanNormalizesClassifiesExtractsResolvesAndStoresOfferMetadata() = runBlocking {
        val body = "فروشگاه مهر ۲۰٪ تخفیف کد تخفیف AbC20 تا پایان امروز"
        val normalizedBody = PersianTextNormalizer().normalize(body)
        assertTrue(normalizedBody.contains("امروز"))
        val source = FakeFlowSmsDataSource(
            listOf(message(1, body)),
        )
        val repository = InMemoryScanRepository()

        val result = scanner(source, repository).scan()

        assertEquals(1, (result as ScanResult.Success).messagesScanned)
        assertEquals(1, result.offersUpserted)
        assertEquals(1, repository.offers.size)
        val offer = repository.offers.getValue(1L)
        assertEquals("AbC20", offer.discountCode)
        assertEquals(listOf(20.0), offer.percentageDiscounts)
        assertNotNull(offer.validUntilMillis)
        assertEquals(SmsCursor(message(1, "").dateMillis, 1L), repository.state?.cursor)
    }

    @Test
    fun repeatedScanIsIncrementalAndNewOffersUpsertWithoutDuplicates() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(1, "۲۰٪ تخفیف کد FIRST20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)

        useCase.scan()
        source.messages += message(2, "۳۰٪ تخفیف کد NEXT30")
        val second = useCase.scan()

        assertEquals(1, (second as ScanResult.Success).messagesScanned)
        assertEquals(listOf(1L, 2L), repository.offers.keys.sorted())
        assertEquals(listOf(null, SmsCursor(message(1, "").dateMillis, 1L)), source.readAfter)
    }

    @Test
    fun fullRescanReextractsAndUpdatesAnExistingOffer() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(5, "۲۰٪ تخفیف کد تخفیف SAME20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        source.messages[0] = message(5, "۴۰٪ تخفیف کد تخفیف SAME40")

        val result = useCase.scan(fullRescan = true)

        assertEquals(1, (result as ScanResult.Success).offersUpserted)
        assertEquals(listOf(5L), repository.offers.keys.toList())
        assertEquals("SAME40", repository.offers.getValue(5L).discountCode)
        assertEquals(listOf(null, null), source.readAfter)
    }

    @Test
    fun emptyInboxCompletesAndUpdatesLastSuccessfulScan() = runBlocking {
        val repository = InMemoryScanRepository()

        val result = scanner(FakeFlowSmsDataSource(emptyList()), repository).scan()

        assertEquals(ScanResult.Success(0, 0, clock.millis()), result)
        assertEquals(clock.millis(), repository.state?.lastSuccessfulScanAtMillis)
    }

    @Test
    fun missingReadPermissionReturnsSafeFailureWithoutPersisting() = runBlocking {
        val source = FakeFlowSmsDataSource(emptyList()).apply { failure = SecurityException("restricted") }
        val repository = InMemoryScanRepository()

        assertEquals(
            ScanResult.Failure(ScanResult.Reason.PermissionDenied),
            scanner(source, repository).scan(),
        )
        assertTrue(repository.offers.isEmpty())
        assertNull(repository.state)
    }

    @Test
    fun duplicateInboxRowsWithSameSmsIdCreateOnlyOneOffer() = runBlocking {
        val row = message(8, "۲۰٪ تخفیف کد ONE20")
        val repository = InMemoryScanRepository()

        val result = scanner(FakeFlowSmsDataSource(listOf(row, row)), repository).scan()

        assertEquals(1, (result as ScanResult.Success).messagesScanned)
        assertEquals(1, repository.offers.size)
    }

    @Test
    fun malformedAndNonOfferMessagesDoNotAbortTheScan() = runBlocking {
        val source = FakeFlowSmsDataSource(
            listOf(
                message(1, null),
                message(2, "سلام، روز خوبی داشته باشید"),
                message(3, "۲۰٪ تخفیف کد VALID20"),
            ),
        )
        val repository = InMemoryScanRepository()

        val result = scanner(source, repository).scan() as ScanResult.Success

        assertEquals(3, result.messagesScanned)
        assertEquals(1, result.offersUpserted)
        assertEquals(1, result.malformedMessages)
        assertEquals(3L, repository.state?.cursor?.smsId)
    }

    @Test
    fun expiredOfferIsStoredWithResolvedExpirationAndAmbiguousDateRemainsUnknown() = runBlocking {
        val source = FakeFlowSmsDataSource(
            listOf(
                message(1, "۲۰٪ تخفیف تا ۱۴۰۳/۰۱/۰۱"),
                message(2, "۳۰٪ تخفیف تا 03/04/2025"),
            ),
        )
        val repository = InMemoryScanRepository()

        val result = scanner(source, repository).scan()

        assertEquals(2, (result as ScanResult.Success).offersUpserted)
        assertTrue(repository.offers.getValue(1).validUntilMillis!! < now.toEpochMilli())
        assertNull(repository.offers.getValue(2).validUntilMillis)
    }

    @Test
    fun cancellationAfterCommittedBatchLeavesCheckpointSafeAndMarksProgressStopped() = runBlocking {
        val committed = CompletableDeferred<Unit>()
        val source = object : SmsDataSource {
            override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
                repeat(SCAN_BATCH_SIZE) { index -> emit(message(index + 1L, null)) }
                awaitCancellation()
            }

            override suspend fun readMessageById(id: Long): SmsMessage? = null
        }
        val repository = InMemoryScanRepository(onCommit = { committed.complete(Unit) })
        val useCase = scanner(source, repository)
        val job = launch(Dispatchers.Default) { useCase.scan() }
        committed.await()

        job.cancelAndJoin()

        assertEquals(SCAN_BATCH_SIZE.toLong(), repository.state?.cursor?.smsId)
        assertEquals(0L, repository.state?.lastSuccessfulScanAtMillis)
        assertFalse(useCase.progress.value.isScanning)
        assertEquals(SCAN_BATCH_SIZE, useCase.progress.value.processedMessages)
    }

    @Test
    fun largeSyntheticInboxStreamsInBoundedCheckpoints() = runBlocking {
        val messageCount = 10_000
        val source = object : SmsDataSource {
            override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
                repeat(messageCount) { index ->
                    emit(message(index + 1L, "ordinary message without an offer"))
                }
            }

            override suspend fun readMessageById(id: Long): SmsMessage? = null
        }
        var commits = 0
        val repository = InMemoryScanRepository(onCommit = { commits++ })
        val noOfferExtractor = OfferExtractor { null }
        val useCase = scanner(source, repository, noOfferExtractor)

        val result = useCase.scan() as ScanResult.Success

        assertEquals(messageCount, result.messagesScanned)
        assertEquals(messageCount, useCase.progress.value.processedMessages)
        assertEquals(0, repository.offers.size)
        assertEquals(messageCount / SCAN_BATCH_SIZE + 1, commits)
        assertEquals(messageCount.toLong(), repository.state?.cursor?.smsId)
    }

    private fun scanner(
        source: SmsDataSource,
        repository: InMemoryScanRepository,
        extractor: OfferExtractor? = null,
    ): ScanSmsUseCase {
        val resolver = PersianDateResolver(clock, zone)
        return ScanSmsUseCase(
            source,
            extractor ?: PersianOfferExtractor(expirationResolver = resolver),
            repository,
            clock,
        )
    }

    private fun message(id: Long, body: String?) = SmsMessage(id, "merchant", body, now.toEpochMilli())

    private class FakeFlowSmsDataSource(messages: List<SmsMessage>) : SmsDataSource {
        val messages = messages.sortedBy { it.id }.toMutableList()
        var failure: Exception? = null
        val readAfter = mutableListOf<SmsCursor?>()

        override fun readInbox(after: SmsCursor?): Flow<SmsMessage> {
            readAfter += after
            return flow {
                failure?.let { throw it }
                messages.sortedBy { it.id }
                    .filter { after == null || it.cursor > after }
                    .forEach { emit(it) }
            }
        }

        override suspend fun readMessageById(id: Long): SmsMessage? =
            messages.firstOrNull { it.id == id }
    }

    private class InMemoryScanRepository(
        private val onCommit: suspend () -> Unit = {},
    ) : ScanRepository {
        val offers = linkedMapOf<Long, Offer>()
        private val scanState = MutableStateFlow<ScanState?>(null)
        val state: ScanState? get() = scanState.value

        override fun observeScanState(): Flow<ScanState?> = scanState
        override suspend fun getScanState(): ScanState? = scanState.value

        override suspend fun commitScan(offers: List<Offer>, state: ScanState) {
            offers.forEach { this.offers[it.sourceSmsId] = it }
            scanState.value = state
            onCommit()
        }
    }
}
