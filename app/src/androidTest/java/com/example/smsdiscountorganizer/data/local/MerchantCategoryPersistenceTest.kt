package com.example.smsdiscountorganizer.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsdiscountorganizer.data.repository.RoomOfferRepository
import com.example.smsdiscountorganizer.data.repository.toDomain
import com.example.smsdiscountorganizer.domain.offer.OfferCategory
import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MerchantCategoryPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun categoriesRoundTripAndManualCorrectionSurvivesRescan() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = RoomOfferRepository(database)
            val extracted = offer().copy(
                merchantName = "دیجی‌کالا",
                productCategory = null,
                applicableCategories = listOf(OfferCategory.Apparel, OfferCategory.Beauty),
                campaignBrand = "دیجی‌پی",
            )
            repository.upsertOffers(listOf(extracted.toDomain()))
            repository.updateMerchantCorrection(50L, "بازار مهر")
            repository.upsertOffers(listOf(extracted.toDomain()))

            val observed = repository.observeOffers().first().single()
            assertEquals("بازار مهر", observed.effectiveMerchantName)
            assertEquals(listOf(OfferCategory.Apparel, OfferCategory.Beauty), observed.applicableCategories)
            assertEquals("دیجی‌پی", observed.campaignBrand)
        } finally {
            database.close()
        }
    }

    @Test
    fun versionFourCategoryMigratesToStableTaxonomyId() = runBlocking {
        val name = "merchant-taxonomy-migration-test.db"
        context.deleteDatabase(name)
        val file: File = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                """CREATE TABLE offers (
                    sourceSmsId INTEGER NOT NULL, offerTitle TEXT, merchantName TEXT,
                    discountCode TEXT, discountValue TEXT, percentageDiscounts TEXT,
                    fixedDiscounts TEXT, freeShipping INTEGER, minimumPurchase TEXT,
                    productCategory TEXT, extractionConfidence REAL NOT NULL,
                    validUntilMillis INTEGER, sourceDateMillis INTEGER NOT NULL,
                    extractedAtMillis INTEGER NOT NULL, PRIMARY KEY(sourceSmsId)
                )""".trimIndent(),
            )
            old.execSQL(
                """INSERT INTO offers (
                    sourceSmsId, offerTitle, merchantName, productCategory,
                    extractionConfidence, sourceDateMillis, extractedAtMillis
                ) VALUES (50, 'Synthetic offer', 'Synthetic shop', 'Electronics', 0.9, 100, 200)""".trimIndent(),
            )
            old.execSQL("CREATE INDEX index_offers_sourceDateMillis_sourceSmsId ON offers (sourceDateMillis, sourceSmsId)")
            old.execSQL("CREATE TABLE scan_state (scanKey TEXT NOT NULL, lastScannedSmsDateMillis INTEGER NOT NULL, lastScannedSmsId INTEGER NOT NULL, lastSuccessfulScanAtMillis INTEGER NOT NULL, PRIMARY KEY(scanKey))")
            old.execSQL("CREATE TABLE app_preferences (`key` TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(`key`))")
            old.version = 4
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        try {
            val row = migrated.offerDao().observeAll().first().single()
            assertEquals(listOf(OfferCategory.Electronics), row.applicableCategories)
            assertNull(row.merchantCorrection)
            assertNull(row.campaignBrand)
            assertEquals(emptyList<SenderMerchantProfileEntity>(), migrated.senderMerchantProfileDao().getAll())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun offer() = OfferEntity(
        sourceSmsId = 50L,
        offerTitle = "Synthetic offer",
        merchantName = null,
        discountCode = null,
        discountValue = null,
        percentageDiscounts = null,
        fixedDiscounts = null,
        freeShipping = null,
        minimumPurchase = null,
        productCategory = ProductCategory.Apparel,
        extractionConfidence = 0.8,
        validUntilMillis = null,
        sourceDateMillis = 100L,
        extractedAtMillis = 200L,
    )
}
