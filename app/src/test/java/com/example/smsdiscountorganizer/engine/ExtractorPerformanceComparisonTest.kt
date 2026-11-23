package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same pure-engine fixture can be copied to historical worktrees for a comparable baseline. */
class ExtractorPerformanceComparisonTest {
    @Test
    fun benchmarksWarmPersianExtractorOnThirtyThousandSyntheticMessages() {
        val extractor = PersianOfferExtractor()
        val warmup = fixture(1_000)
        warmup.forEach(extractor::extract)
        val rows = fixture(30_000)

        val startedAt = System.nanoTime()
        val offers = rows.count { extractor.extract(it) != null }
        val elapsedNanos = System.nanoTime() - startedAt

        assertEquals(1_500, offers)
        println(
            "PERF_EXTRACTOR commit=${System.getProperty("benchmark.commit", "working-tree")} " +
                "n=${rows.size} duration_ms=${elapsedNanos / 1_000_000} " +
                "avg_us_per_sms=${elapsedNanos / rows.size / 1_000.0} offers=$offers",
        )
    }

    private fun fixture(size: Int): List<SmsMessage> = List(size) { index ->
        val body = if (index % 20 == 0) {
            "۲۰٪ تخفیف برای خرید گوشی؛ کد SAVE$index"
        } else {
            "پرداخت شماره ${index + 10_000} انجام شد."
        }
        SmsMessage(index + 1L, "PROMO_${index % 40}", body, 1_700_000_000_000L + index)
    }
}
