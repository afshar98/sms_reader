package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.domain.sms.cursor
import com.example.smsdiscountorganizer.engine.SenderMerchantProfileBuilder
import com.example.smsdiscountorganizer.engine.extraction.MerchantExtractor
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import com.example.smsdiscountorganizer.engine.expiration.PersianDateResolver
import com.example.smsdiscountorganizer.engine.classification.DiscountClassifier
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Opt-in, privacy-safe benchmark: run with `--tests '*ScanPerformanceBenchmarkTest'`. */
class ScanPerformanceBenchmarkTest {
    @Test
    fun benchmarksSyntheticPipelineAtOneTenAndThirtyThousandMessages() = runBlocking {
        val sizes = listOf(1_000, 10_000, 30_000)
        val warmed = fixture(1_000)
        runScan(warmed)

        sizes.forEach { size ->
            val rows = fixture(size)
            val dataSource = BenchmarkSmsDataSource(rows)
            val repository = BenchmarkScanRepository()
            val useCase = ScanSmsUseCase(
                dataSource,
                PersianOfferExtractor(
                    expirationResolver = PersianDateResolver(
                        Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Asia/Tehran")),
                        ZoneId.of("Asia/Tehran"),
                    ),
                ),
                repository,
                Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Asia/Tehran")),
            )
            val progressEvents = AtomicInteger()
            val scanStartedAtNanos = AtomicLong(0L)
            val tapToScanningNanos = AtomicLong(-1L)
            val tapToFirstProcessedNanos = AtomicLong(-1L)
            val progressJob = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch(
                start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED,
            ) {
                useCase.progress.collect { progress ->
                    progressEvents.incrementAndGet()
                    val scanStarted = scanStartedAtNanos.get()
                    if (scanStarted != 0L && progress.isScanning) {
                        tapToScanningNanos.compareAndSet(-1L, System.nanoTime() - scanStarted)
                    }
                    if (scanStarted != 0L && progress.processedMessages > 0) {
                        tapToFirstProcessedNanos.compareAndSet(-1L, System.nanoTime() - scanStarted)
                    }
                }
            }
            val heapPools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
            heapPools.forEach { runCatching { it.resetPeakUsage() } }
            val startedAt = System.nanoTime()
            scanStartedAtNanos.set(startedAt)
            val result = useCase.scan()
            val elapsedNanos = System.nanoTime() - startedAt
            val peakHeapBytes = heapPools.maxOfOrNull { it.peakUsage?.used ?: 0L } ?: 0L
            progressJob.cancel()

            assertEquals(size, (result as ScanResult.Success).messagesScanned)
            assert(progressEvents.get() < size / 20 + 100) {
                "Progress emissions should stay bounded: ${progressEvents.get()} for $size messages"
            }
            val offerCount = result.offersUpserted
            val seconds = elapsedNanos / 1_000_000_000.0
            println(
                "PERF_BASELINE n=$size duration_ms=${elapsedNanos / 1_000_000} " +
                    "avg_ms_per_sms=${elapsedNanos / size / 1_000_000.0} " +
                    "sms_per_second=${(size / seconds).toLong()} offers=$offerCount " +
                    "source_reads=${dataSource.readCalls} progress_events=${progressEvents.get()} " +
                    "tap_to_scanning_ms=${tapToScanningNanos.get() / 1_000_000} " +
                    "tap_to_first_processed_ms=${tapToFirstProcessedNanos.get() / 1_000_000} " +
                    "peak_jvm_heap_mb=${peakHeapBytes / (1024 * 1024)}",
            )
            if (size == 30_000) {
                val repeatStartedAt = System.nanoTime()
                val repeated = useCase.scan() as ScanResult.Success
                val repeatElapsedNanos = System.nanoTime() - repeatStartedAt
                assertEquals(0, repeated.messagesScanned)
                assertEquals(offerCount, repository.offerCount)
                println(
                    "PERF_REPEAT n=$size duration_ms=${repeatElapsedNanos / 1_000_000} " +
                        "messages_scanned=${repeated.messagesScanned} source_reads=${dataSource.readCalls} " +
                        "stored_offers=${repository.offerCount}",
                )
            }
        }

        benchmarkStages(fixture(30_000))
    }

    private fun benchmarkStages(rows: List<SmsMessage>) {
        val sample = rows.filterIndexed { index, _ -> index % 10 == 0 }
        val stages = linkedMapOf<String, () -> Unit>(
            "normalize" to {
                val normalizer = PersianTextNormalizer()
                sample.forEach { normalizer.normalize(it.body.orEmpty()) }
            },
            "classify" to {
                val classifier = DiscountClassifier()
                sample.forEach { classifier.classify(it.body.orEmpty()) }
            },
            "merchant" to {
                val extractor = MerchantExtractor()
                sample.forEach { extractor.extractEvidenceNames(it.body.orEmpty()) }
            },
            "date" to {
                val resolver = PersianDateResolver(
                    Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Asia/Tehran")),
                    ZoneId.of("Asia/Tehran"),
                )
                sample.forEach { resolver.resolve(it.body, it.dateMillis) }
            },
            "sender_profiles" to {
                val builder = SenderMerchantProfileBuilder()
                rows.forEach(builder::add)
                builder.fingerprint()
            },
            "full_extractor" to {
                val extractor = PersianOfferExtractor()
                rows.forEach(extractor::extract)
            },
        )
        stages.forEach { (name, block) ->
            val startedAt = System.nanoTime()
            block()
            val elapsedNanos = System.nanoTime() - startedAt
            val measuredRows = if (name == "sender_profiles" || name == "full_extractor") rows.size else sample.size
            println(
                "PERF_STAGE name=$name rows=$measuredRows duration_ms=${elapsedNanos / 1_000_000} " +
                    "avg_us_per_sms=${elapsedNanos / measuredRows / 1_000.0}",
            )
        }
    }

    private fun fixture(size: Int): List<SmsMessage> = List(size) { index ->
        val body = when {
            index == 0 -> "کد تخفیف تپسی‌مارکت تا امشب اعتبار داره!\nکد: xyzp"
            index == 1 -> "هندزفری و پلاک طلا ۸۰٪\n۱۲ظهر، قسطی‌جمعه دیجی‌پی"
            index % 20 == 0 -> "دیجی‌کالا ${20 + index % 70}٪ تخفیف خرید گوشی؛ کد: SAVE$index"
            index % 3 == 0 -> "پرداخت شماره ${100_000 + index} انجام شد."
            else -> "پیام اطلاع‌رسانی فروشگاه درباره خدمات مشتریان ${index % 11}"
        }
        SmsMessage(
            id = index + 1L,
            address = "PROMO_${index % 40}",
            body = body,
            dateMillis = 1_700_000_000_000L + index,
        )
    }

    private suspend fun runScan(rows: List<SmsMessage>) = ScanSmsUseCase(
        BenchmarkSmsDataSource(rows),
        PersianOfferExtractor(),
        BenchmarkScanRepository(),
        Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Asia/Tehran")),
    ).scan()

    private class BenchmarkSmsDataSource(private val rows: List<SmsMessage>) : SmsDataSource {
        var readCalls: Int = 0

        override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
            readCalls++
            rows.forEach { if (after == null || it.cursor > after) emit(it) }
        }

        override suspend fun readMessageById(id: Long): SmsMessage? = rows.firstOrNull { it.id == id }
    }

    private class BenchmarkScanRepository : ScanRepository {
        private val offers = linkedMapOf<Long, Offer>()
        private val scanState = MutableStateFlow<ScanState?>(null)
        private var profiles = emptyList<SenderMerchantProfile>()
        val offerCount: Int get() = offers.size

        override fun observeScanState(): Flow<ScanState?> = scanState
        override suspend fun getScanState(): ScanState? = scanState.value
        override suspend fun replaceSenderProfiles(profiles: List<SenderMerchantProfile>, fingerprint: String) {
            this.profiles = profiles
            scanState.value = (scanState.value ?: ScanState(null, 0L)).copy(senderProfileFingerprint = fingerprint)
        }
        override suspend fun getSenderProfiles(): List<SenderMerchantProfile> = profiles
        override suspend fun commitScan(offers: List<Offer>, state: ScanState) {
            offers.forEach { this.offers[it.sourceSmsId] = it }
            scanState.value = state
        }
    }
}
