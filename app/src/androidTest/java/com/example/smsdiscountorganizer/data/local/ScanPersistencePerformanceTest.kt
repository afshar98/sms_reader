package com.example.smsdiscountorganizer.data.local

import android.content.Context
import androidx.room.Room
import com.example.smsdiscountorganizer.data.repository.RoomScanRepository
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.scan.ScanState
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side SQLite benchmark uses synthetic metadata only; it never reads the SMS provider. */
@RunWith(AndroidJUnit4::class)
class ScanPersistencePerformanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun benchmarksSyntheticOfferWriteAndFlowQueryAtThirtyThousandInboxScale() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = RoomScanRepository(database)
            val syntheticOffers = List(1_500) { index ->
                Offer(
                    sourceSmsId = index + 1L,
                    title = "پیشنهاد آزمایشی ${index + 1}",
                    merchantName = "فروشگاه ${index % 25}",
                    discountCode = "TEST${index + 1}",
                    discountValue = "20%",
                    percentageDiscounts = listOf(20.0),
                    fixedDiscounts = null,
                    freeShipping = null,
                    minimumPurchase = null,
                    productCategory = null,
                    extractionConfidence = 0.9,
                    validUntilMillis = null,
                    sourceDateMillis = index.toLong(),
                    extractedAtMillis = 1_700_000_000_000L,
                )
            }
            val state = ScanState(
                cursor = SmsCursor(1_700_000_000_000L, 30_000L),
                lastSuccessfulScanAtMillis = 1_700_000_000_000L,
            )

            val writeStartedAt = System.nanoTime()
            repository.commitScan(syntheticOffers, state)
            val writeMillis = (System.nanoTime() - writeStartedAt) / 1_000_000

            val queryStartedAt = System.nanoTime()
            val storedOffers = database.offerDao().observeAll().first()
            val queryMillis = (System.nanoTime() - queryStartedAt) / 1_000_000

            assertEquals(1_500, storedOffers.size)
            println("PERF_ROOM offers=${storedOffers.size} transactional_write_ms=$writeMillis flow_query_ms=$queryMillis")
        } finally {
            database.close()
        }
    }
}
