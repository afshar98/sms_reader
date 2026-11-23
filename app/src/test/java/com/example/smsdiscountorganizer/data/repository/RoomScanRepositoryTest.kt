package com.example.smsdiscountorganizer.data.repository

import androidx.room.Room
import com.example.smsdiscountorganizer.data.local.AppDatabase
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.scan.ScanState
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.app.Application
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomScanRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomScanRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomScanRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun commitScanWritesOffersAndCheckpointTogether() = runBlocking {
        repository.commitScan(
            offers = listOf(offer(72L)),
            state = ScanState(SmsCursor(300L, 72L), 400L),
        )

        assertEquals(1, database.offerDao().count())
        assertEquals(SmsCursor(300L, 72L), repository.getScanState()?.cursor)
        assertEquals(SmsCursor(300L, 72L), repository.observeScanState().first()?.cursor)
    }

    @Test
    fun failedCheckpointWriteRollsBackOfferUpserts() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_scan_state BEFORE INSERT ON scan_state
               BEGIN SELECT RAISE(ABORT, 'forced test failure'); END""",
        )

        assertThrows(Exception::class.java) {
            runBlocking {
                repository.commitScan(
                    offers = listOf(offer(72L)),
                    state = ScanState(SmsCursor(300L, 72L), 400L),
                )
            }
        }

        assertEquals(0, database.offerDao().count())
        assertEquals(null, repository.getScanState())
    }

    @Test
    fun replacingSenderSnapshotIsAtomicAndPersistsOnlyDerivedEvidence() = runBlocking {
        val profile = SenderMerchantProfile(
            senderFingerprint = "a".repeat(64),
            merchantSupport = mapOf("دیجی‌کالا" to 3),
            totalSupportingMessages = 3,
            isConflicted = false,
            isSharedGateway = false,
            confidence = 0.75,
            resolvedMerchantName = "دیجی‌کالا",
        )

        repository.replaceSenderProfiles(listOf(profile), "profile-version")

        assertEquals(listOf(profile), repository.getSenderProfiles())
        assertEquals("profile-version", repository.getScanState()?.senderProfileFingerprint)
        assertEquals(3, database.senderMerchantProfileDao().getAll().single().supportingMessages)
    }

    @Test
    fun failedSnapshotReplacementKeepsPreviousSenderProfiles() = runBlocking {
        val first = profile("دیجی‌کالا", "one")
        repository.replaceSenderProfiles(listOf(first), "first")
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_profile_fingerprint_insert BEFORE INSERT ON scan_state
               BEGIN SELECT RAISE(ABORT, 'forced test failure'); END""",
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_profile_fingerprint_update BEFORE UPDATE ON scan_state
               BEGIN SELECT RAISE(ABORT, 'forced test failure'); END""",
        )

        assertThrows(Exception::class.java) {
            runBlocking { repository.replaceSenderProfiles(listOf(profile("اسنپ‌فود", "two")), "second") }
        }

        assertEquals(listOf(first), repository.getSenderProfiles())
        assertEquals("first", repository.getScanState()?.senderProfileFingerprint)
    }

    private fun profile(merchant: String, fingerprint: String) = SenderMerchantProfile(
        senderFingerprint = fingerprint,
        merchantSupport = mapOf(merchant to 3),
        totalSupportingMessages = 3,
        isConflicted = false,
        isSharedGateway = false,
        confidence = 0.75,
        resolvedMerchantName = merchant,
    )

    private fun offer(sourceSmsId: Long) = Offer(
        sourceSmsId = sourceSmsId,
        title = "Example offer",
        merchantName = null,
        discountCode = "DEMO10",
        discountValue = "10%",
        percentageDiscounts = listOf(10.0),
        fixedDiscounts = null,
        freeShipping = null,
        minimumPurchase = null,
        productCategory = null,
        extractionConfidence = 0.9,
        validUntilMillis = null,
        sourceDateMillis = 300L,
        extractedAtMillis = 400L,
    )
}
