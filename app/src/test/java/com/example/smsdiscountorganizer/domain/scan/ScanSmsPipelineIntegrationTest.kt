package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
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
import org.junit.Assert.assertNotEquals
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
        assertEquals(listOf(null, null), source.readAfter)
    }

    @Test
    fun repeatedScanRevisitsWindowButOnlyProcessesNewRowsWithoutDuplicates() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(1, "۲۰٪ تخفیف کد FIRST20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)

        useCase.scan()
        source.messages += message(2, "۳۰٪ تخفیف کد NEXT30")
        val second = useCase.scan()

        assertEquals(2, (second as ScanResult.Success).messagesScanned)
        assertEquals(1, second.messagesUnchanged)
        assertEquals(1, second.offersUpserted)
        assertEquals(listOf(1L, 2L), repository.offers.keys.sorted())
        assertEquals(
            listOf(null, null, SmsCursor(message(1, "").dateMillis, 1L), null),
            source.readAfter,
        )
    }

    @Test
    fun editedMessageWithAnExistingProviderIdIsReprocessedAndUpdatesItsOffer() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(7, "۲۰٪ تخفیف کد تخفیف OLD20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        source.messages[0] = message(7, "۴۰٪ تخفیف کد تخفیف NEW40")

        val result = useCase.scan() as ScanResult.Success

        assertEquals(1, result.messagesScanned)
        assertEquals(0, result.messagesUnchanged)
        assertEquals("NEW40", repository.offers.getValue(7L).discountCode)
        assertEquals(listOf(40.0), repository.offers.getValue(7L).percentageDiscounts)
    }

    @Test
    fun editedMessageThatStopsBeingAnOfferRemovesOnlyItsDerivedOffer() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(8, "۲۰٪ تخفیف کد تخفیف OLD20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        source.messages[0] = message(8, "سلام، روز خوبی داشته باشید")

        useCase.scan()

        assertTrue(repository.offers.isEmpty())
        assertEquals(false, repository.processedRecords.single().producedOffer)
    }

    @Test
    fun completedRangeReconciliationRemovesRowsDeletedOutsideTheApp() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(9, "۲۰٪ تخفیف کد تخفیف KEEP20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        source.messages.clear()

        val result = useCase.scan() as ScanResult.Success

        assertEquals(0, result.messagesScanned)
        assertTrue(repository.offers.isEmpty())
        assertTrue(repository.processedRecords.isEmpty())
    }

    @Test
    fun newSenderEvidenceWithoutResolutionChangeDoesNotReprocessOldOffers() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(1, "۲۰٪ تخفیف کد OLD20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        source.messages += message(2, "نام فروشگاه: اسنپ‌فود")

        val result = useCase.scan() as ScanResult.Success

        assertEquals(2, result.messagesScanned)
        assertEquals(1, result.messagesUnchanged)
        assertNull(repository.offers.getValue(1L).merchantName)
        assertEquals(listOf(null, null, SmsCursor(message(1, "").dateMillis, 1L), null), source.readAfter)
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
        assertEquals(listOf(null, null, null), source.readAfter)
    }

    @Test
    fun fullRescanReprocessesPreviouslyRejectedMessagesWithoutDuplicatingOffers() = runBlocking {
        val source = FakeFlowSmsDataSource(
            listOf(
                message(31, "کد تخفیف تپسی‌مارکت تا امشب اعتبار داره! کد: xyzp"),
                message(32, "هندزفری و پلاک طلا ۸۰٪؛ ۱۲ظهر، قسطی‌جمعه دیجی‌پی"),
            ),
        )
        val repository = InMemoryScanRepository()

        val oldRulesResult = scanner(source, repository, OfferExtractor { null }).scan()
        assertEquals(0, (oldRulesResult as ScanResult.Success).offersUpserted)
        assertTrue(repository.offers.isEmpty())

        val rescanned = scanner(source, repository).scan(fullRescan = true) as ScanResult.Success
        val repeated = scanner(source, repository).scan(fullRescan = true) as ScanResult.Success

        assertEquals(2, rescanned.offersUpserted)
        assertEquals(2, repeated.offersUpserted)
        assertEquals(listOf(31L, 32L), repository.offers.keys.sorted())
        assertEquals("دیجی‌پی", repository.offers.getValue(32L).campaignBrand)
        assertNull(repository.offers.getValue(32L).merchantName)
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
        var readCount = 0
        val source = object : SmsDataSource {
            override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
                readCount++
                repeat(SCAN_BATCH_SIZE) { index -> emit(message(index + 1L, null)) }
                if (readCount == 2) awaitCancellation()
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

    @Test
    fun profileChangeReprocessesOldOffersAndCurrentMessageEvidenceWins() = runBlocking {
        val source = FakeFlowSmsDataSource(listOf(message(1, "۲۰٪ تخفیف کد SAME20")))
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        assertNull(repository.offers.getValue(1L).merchantName)

        source.messages += message(2, "نام فروشگاه: اسنپ‌فود")
        source.messages += message(3, "نام فروشگاه: اسنپ‌فود")
        source.messages += message(4, "نام فروشگاه: اسنپ‌فود")
        val changed = useCase.scan()

        assertEquals(4, (changed as ScanResult.Success).messagesScanned)
        assertEquals("اسنپ‌فود", repository.offers.getValue(1L).merchantName)
        assertEquals(listOf(1L), repository.offers.keys.toList())
        assertEquals(
            listOf(null, null, SmsCursor(message(1, "").dateMillis, 1L), null),
            source.readAfter,
        )
    }

    @Test
    fun historicalMerchantDoesNotSupplyCurrentMessageCategory() = runBlocking {
        val profileSource = FakeFlowSmsDataSource(
            listOf(
                message(1, "نام فروشگاه: اسنپ‌فود"),
                message(2, "نام فروشگاه: اسنپ‌فود"),
                message(3, "نام فروشگاه: اسنپ‌فود"),
                message(4, "۲۰٪ تخفیف کد AMBIGUOUS20"),
            ),
        )
        val repository = InMemoryScanRepository()

        scanner(profileSource, repository).scan()

        assertEquals("اسنپ‌فود", repository.offers.getValue(4L).merchantName)
        assertEquals(listOf(com.example.smsdiscountorganizer.domain.offer.OfferCategory.UNKNOWN), repository.offers.getValue(4L).applicableCategories)
    }

    @Test
    fun currentMessageBrandOverridesConflictingSenderHistory() = runBlocking {
        val source = FakeFlowSmsDataSource(
            listOf(
                message(1, "نام فروشگاه: اسنپ‌فود"),
                message(2, "نام فروشگاه: اسنپ‌فود"),
                message(3, "نام فروشگاه: اسنپ‌فود"),
                message(4, "دیجی‌کالا ۲۰٪ تخفیف کد DIGI20"),
            ),
        )
        val repository = InMemoryScanRepository()

        scanner(source, repository).scan()

        assertEquals("دیجی‌کالا", repository.offers.getValue(4L).merchantName)
    }

    @Test
    fun cancelledFullRescanRemainsPendingAndNextScanReprocessesAllOffers() = runBlocking {
        var reads = 0
        var cancelProcessingPass = false
        val processingPassStarted = CompletableDeferred<Unit>()
        val source = object : SmsDataSource {
            var current = message(1, "۲۰٪ تخفیف کد تخفیف OLD20")

            override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
                reads += 1
                emit(current)
                if (cancelProcessingPass && reads == 3) {
                    processingPassStarted.complete(Unit)
                    awaitCancellation()
                }
            }

            override suspend fun readMessageById(id: Long): SmsMessage? = current.takeIf { it.id == id }
        }
        val repository = InMemoryScanRepository()
        val useCase = scanner(source, repository)
        useCase.scan()
        source.current = message(1, "۴۰٪ تخفیف کد تخفیف NEW40")
        cancelProcessingPass = true

        val rescanJob = launch(Dispatchers.Default) { useCase.scan(fullRescan = true) }
        processingPassStarted.await()
        rescanJob.cancelAndJoin()

        assertNotEquals(repository.state?.senderProfileFingerprint, repository.state?.offersSenderProfileFingerprint)
        cancelProcessingPass = false
        val resumed = useCase.scan()

        assertEquals(1, (resumed as ScanResult.Success).messagesScanned)
        assertEquals("NEW40", repository.offers.getValue(1L).discountCode)
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
        private var profiles: List<SenderMerchantProfile> = emptyList()
        private val scanState = MutableStateFlow<ScanState?>(null)
        private val processed = linkedMapOf<Long, ProcessedSmsRecord>()
        val processedRecords: List<ProcessedSmsRecord> get() = processed.values.toList()
        val state: ScanState? get() = scanState.value

        override fun observeScanState(): Flow<ScanState?> = scanState
        override suspend fun getScanState(): ScanState? = scanState.value

        override suspend fun replaceSenderProfiles(profiles: List<SenderMerchantProfile>, fingerprint: String) {
            this.profiles = profiles
            scanState.value = (scanState.value ?: ScanState(null, 0L)).copy(senderProfileFingerprint = fingerprint)
        }

        override suspend fun getSenderProfiles(): List<SenderMerchantProfile> = profiles

        override suspend fun getProcessedSms(dateRange: SmsDateRange?): List<ProcessedSmsRecord> = processed.values.toList()

        override suspend fun commitScan(
            offers: List<Offer>,
            state: ScanState,
            processedMessages: List<ProcessedSmsRecord>,
            deleteOfferIds: List<Long>,
            reconciliation: ScanReconciliation?,
        ) {
            offers.forEach { this.offers[it.sourceSmsId] = it }
            processedMessages.forEach { processed[it.smsId] = it }
            deleteOfferIds.forEach { this.offers.remove(it) }
            reconciliation?.let { r ->
                this.offers.keys.removeAll { id -> id !in r.observedSmsIds }
                processed.keys.removeAll { id -> id !in r.observedSmsIds }
            }
            scanState.value = state
            onCommit()
        }
    }
}
