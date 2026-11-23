package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.scan.ScanRepository
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.OfferExtractor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanSmsUseCaseTest {
    private val scannedAt = Instant.parse("2026-11-22T10:00:00Z")

    @Test
    fun repeatedScansUpsertBySmsIdAndIncludeLaterIdsWithEqualTimestamps() = runBlocking {
        val messages = listOf(
            SmsMessage(11L, null, "private body 11", 100L),
            SmsMessage(10L, null, "private body 10", 100L),
            SmsMessage(12L, null, "private backdated body", 50L),
        )
        val source = RepeatingSmsDataSource(messages)
        val offers = InMemoryOfferRepository()
        val scans = InMemoryScanRepository(offers, ScanState(SmsCursor(100L, 9L), 0L))
        val useCase = ScanSmsUseCase(source, TestOfferExtractor(), scans, fixedClock())

        val first = useCase.scan()
        val second = useCase.scan()

        assertEquals(ScanResult.Success(3, 3, scannedAt.toEpochMilli()), first)
        assertEquals(ScanResult.Success(3, 0, scannedAt.toEpochMilli(), messagesUnchanged = 3), second)
        assertEquals(listOf(10L, 11L, 12L), offers.offers.keys.sorted())
        assertEquals(SmsCursor(50L, 12L), scans.state?.cursor)
        assertEquals(
            listOf(null, null, SmsCursor(50L, 12L), null),
            source.cursors,
        )
        assertEquals(10L, offers.offers.getValue(10L).sourceSmsId)
        assertEquals("Saved offer", offers.offers.getValue(10L).title)
        assertEquals("Merchant", offers.offers.getValue(10L).merchantName)
        assertEquals(listOf(20.0), offers.offers.getValue(10L).percentageDiscounts)
        assertEquals(MonetaryAmount("5000", MoneyCurrency.Toman), offers.offers.getValue(10L).minimumPurchase)
        assertEquals(ProductCategory.Apparel, offers.offers.getValue(10L).productCategory)
    }

    @Test
    fun cancellationPropagatesWithoutCommittingOffersOrCheckpoint() = runBlocking {
        val source = object : SmsDataSource {
            override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
                throw CancellationException("cancel scan")
            }

            override suspend fun readMessageById(id: Long): SmsMessage? = null
        }
        val offers = InMemoryOfferRepository()
        val scans = InMemoryScanRepository(offers)
        val useCase = ScanSmsUseCase(source, TestOfferExtractor(), scans, fixedClock())

        assertThrows(CancellationException::class.java) { runBlocking { useCase.scan() } }
        assertEquals(emptyMap<Long, Offer>(), offers.offers)
        assertEquals(null, scans.state)
    }

    private fun fixedClock(): Clock = Clock.fixed(scannedAt, ZoneOffset.UTC)

    private class TestOfferExtractor : OfferExtractor {
        override fun extract(message: SmsMessage): OfferMetadata =
            OfferMetadata(
                merchantName = "Merchant",
                offerTitle = "Saved offer",
                percentageDiscounts = listOf(20.0),
                fixedDiscounts = null,
                freeShipping = null,
                couponCode = null,
                minimumPurchase = MonetaryAmount("5000", MoneyCurrency.Toman),
                productCategory = ProductCategory.Apparel,
                confidence = 0.9,
            )
    }

    private class RepeatingSmsDataSource(private val messages: List<SmsMessage>) : SmsDataSource {
        val cursors = mutableListOf<SmsCursor?>()

        override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
            cursors += after
            messages.sortedBy { it.id }.forEach { emit(it) }
        }

        override suspend fun readMessageById(id: Long): SmsMessage? =
            messages.firstOrNull { it.id == id }
    }

    private class InMemoryOfferRepository : OfferRepository {
        val offers = linkedMapOf<Long, Offer>()

        override fun observeOffers(): Flow<List<Offer>> = flowOf(offers.values.toList())

        override suspend fun upsertOffers(offers: List<Offer>) {
            offers.forEach { this.offers[it.sourceSmsId] = it }
        }

        override suspend fun updateMerchantCorrection(sourceSmsId: Long, merchantName: String?) {
            offers[sourceSmsId]?.let { offers[sourceSmsId] = it.copy(merchantCorrection = merchantName?.trim()?.takeIf(String::isNotEmpty)) }
        }

        override suspend fun clearAllLocalData() = offers.clear()
    }

    private class InMemoryScanRepository(
        private val offers: InMemoryOfferRepository,
        initialState: ScanState? = null,
    ) : ScanRepository {
        private var profiles: List<SenderMerchantProfile> = emptyList()
        private val processed = linkedMapOf<Long, ProcessedSmsRecord>()
        var state = initialState
            private set

        override fun observeScanState(): Flow<ScanState?> = flowOf(state)

        override suspend fun getScanState(): ScanState? = state

        override suspend fun replaceSenderProfiles(profiles: List<SenderMerchantProfile>, fingerprint: String) {
            this.profiles = profiles
            state = (state ?: ScanState(null, 0L)).copy(senderProfileFingerprint = fingerprint)
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
            this.offers.upsertOffers(offers)
            processedMessages.forEach { processed[it.smsId] = it }
            deleteOfferIds.forEach { this.offers.offers.remove(it) }
            reconciliation?.let { r ->
                this.offers.offers.keys.removeAll { id -> id !in r.observedSmsIds }
                processed.keys.removeAll { id -> id !in r.observedSmsIds }
            }
            this.state = state
        }
    }
}
