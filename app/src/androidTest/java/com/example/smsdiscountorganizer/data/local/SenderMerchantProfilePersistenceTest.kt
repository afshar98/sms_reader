package com.example.smsdiscountorganizer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsdiscountorganizer.data.repository.RoomOfferRepository
import com.example.smsdiscountorganizer.data.repository.RoomScanRepository
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.scan.ScanState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SenderMerchantProfilePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun replacingProfilesPersistsDerivedEvidenceAndFingerprintOnly() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomScanRepository(database)
            val profile = profile("دیجی‌کالا", "a")

            repository.replaceSenderProfiles(listOf(profile), "version-one")

            assertEquals(listOf(profile), repository.getSenderProfiles())
            assertEquals("version-one", repository.getScanState()?.senderProfileFingerprint)
            assertEquals(3, database.senderMerchantProfileDao().getAll().single().supportingMessages)
        } finally {
            database.close()
        }
    }

    @Test
    fun failedProfileReplacementRollsBackToPreviousSnapshot() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomScanRepository(database)
            val first = profile("دیجی‌کالا", "a")
            repository.replaceSenderProfiles(listOf(first), "version-one")
            database.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER fail_sender_profile_update BEFORE UPDATE ON scan_state
                   BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END""",
            )
            database.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER fail_sender_profile_insert BEFORE INSERT ON scan_state
                   BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END""",
            )

            assertThrows(Exception::class.java) {
                runBlocking { repository.replaceSenderProfiles(listOf(profile("اسنپ‌فود", "b")), "version-two") }
            }

            assertEquals(listOf(first), repository.getSenderProfiles())
            assertEquals("version-one", repository.getScanState()?.senderProfileFingerprint)
        } finally {
            database.close()
        }
    }

    @Test
    fun clearingLocalDataAlsoRemovesSenderProfiles() = runBlocking {
        val database = inMemoryDatabase()
        try {
            RoomScanRepository(database).replaceSenderProfiles(listOf(profile("تپسی", "c")), "version")

            RoomOfferRepository(database).clearAllLocalData()

            assertEquals(emptyList<SenderMerchantProfile>(), RoomScanRepository(database).getSenderProfiles())
            assertEquals(null, RoomScanRepository(database).getScanState())
        } finally {
            database.close()
        }
    }

    @Test
    fun offerReprocessingPreservesManualMerchantCorrection() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val offers = RoomOfferRepository(database)
            val scans = RoomScanRepository(database)
            val extracted = offer("Original merchant")
            offers.upsertOffers(listOf(extracted))
            offers.updateMerchantCorrection(extracted.sourceSmsId, "Manual merchant")

            scans.commitScan(listOf(extracted.copy(merchantName = "Reclassified merchant")), ScanState(null, 0L))

            assertEquals("Manual merchant", offers.observeOffers().first().single().effectiveMerchantName)
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    private fun profile(merchant: String, fingerprint: String) = SenderMerchantProfile(
        senderFingerprint = fingerprint,
        merchantSupport = mapOf(merchant to 3),
        totalSupportingMessages = 3,
        isConflicted = false,
        isSharedGateway = false,
        confidence = 0.75,
        resolvedMerchantName = merchant,
    )

    private fun offer(merchant: String) = Offer(
        sourceSmsId = 50L,
        title = "Synthetic offer",
        merchantName = merchant,
        discountCode = "TEST50",
        discountValue = "10%",
        percentageDiscounts = listOf(10.0),
        fixedDiscounts = null,
        freeShipping = null,
        minimumPurchase = null,
        productCategory = null,
        extractionConfidence = 0.9,
        validUntilMillis = null,
        sourceDateMillis = 100L,
        extractedAtMillis = 200L,
    )
}
