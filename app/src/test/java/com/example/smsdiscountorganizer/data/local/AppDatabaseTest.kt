package com.example.smsdiscountorganizer.data.local

import android.database.sqlite.SQLiteDatabase
import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.smsdiscountorganizer.data.repository.RoomOfferRepository
import com.example.smsdiscountorganizer.data.repository.toDomain
import com.example.smsdiscountorganizer.domain.offer.OfferCategory
import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppDatabaseTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun repeatedUpsertKeepsOneOfferPerSourceSmsAndFlowObservesUpdates() = runBlocking {
        val initial = offer(sourceSmsId = 44L, title = "Ten percent")
        database.offerDao().upsertAll(listOf(initial))
        database.offerDao().upsertAll(listOf(initial.copy(offerTitle = "Twenty percent")))

        assertEquals(1, database.offerDao().count())
        assertEquals("Twenty percent", database.offerDao().observeAll().first().single().offerTitle)
    }

    @Test
    fun structuredOfferFieldsRoundTripThroughRoomConverters() = runBlocking {
        val structured = offer(47L, title = null).copy(
            percentageDiscounts = listOf(10.0, 12.5),
            fixedDiscounts = listOf(MonetaryAmount("5000", MoneyCurrency.Toman)),
            freeShipping = true,
            minimumPurchase = MonetaryAmount("100000", MoneyCurrency.Rial),
            productCategory = ProductCategory.Electronics,
            extractionConfidence = 0.87,
            applicableCategories = listOf(OfferCategory.Electronics, OfferCategory.Beauty),
            merchantCorrection = "بازار مهر",
            campaignBrand = "دیجی‌پی",
        )

        database.offerDao().upsertAll(listOf(structured))

        assertEquals(structured, database.offerDao().observeAll().first().single())
    }

    @Test
    fun merchantCorrectionSurvivesOfferRescanUpserts() = runBlocking {
        val repository = RoomOfferRepository(database)
        val extracted = offer(48L, "Offer").copy(merchantName = "دیجی‌کالا")
        repository.upsertOffers(listOf(extracted.toDomain()))
        repository.updateMerchantCorrection(48L, "بازار مهر")

        repository.upsertOffers(listOf(extracted.toDomain()))

        assertEquals("بازار مهر", repository.observeOffers().first().single().effectiveMerchantName)
        assertEquals("بازار مهر", repository.observeOffers().first().single().merchantCorrection)
    }

    @Test
    fun offerListOrderingUsesTheCompositeSortIndex() {
        database.query(
            SimpleSQLiteQuery(
                "EXPLAIN QUERY PLAN SELECT * FROM offers ORDER BY sourceDateMillis DESC, sourceSmsId DESC",
            ),
        ).use { cursor ->
            val detailsColumn = cursor.getColumnIndexOrThrow("detail")
            val details = buildList {
                while (cursor.moveToNext()) add(cursor.getString(detailsColumn))
            }
            assertTrue(details.any { it.contains("index_offers_sourceDateMillis_sourceSmsId") })
        }
    }

    @Test
    fun clearingLocalDataRemovesOffersPreferencesAndScanState() = runBlocking {
        database.offerDao().upsertAll(listOf(offer(44L, "Ten percent")))
        database.appPreferenceDao().save(AppPreferenceEntity("theme", true))
        database.scanStateDao().upsert(ScanStateEntity("inbox", 100L, 44L, 200L))
        database.senderMerchantProfileDao().insertAll(
            listOf(SenderMerchantProfileEntity("a".repeat(64), "دیجی‌کالا", 3, 3, false, false, 0.75, "دیجی‌کالا")),
        )

        RoomOfferRepository(database).clearAllLocalData()

        assertEquals(0, database.offerDao().count())
        assertEquals(null, database.appPreferenceDao().observe("theme").first())
        assertEquals(null, database.scanStateDao().get())
        assertEquals(0, database.senderMerchantProfileDao().getAll().size)
    }

    @Test
    fun migrationFromVersionOnePreservesPreferencesAndCreatesScanTables() = runBlocking {
        database.close()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val oldFile: File = context.getDatabasePath(name)
        oldFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(oldFile, null).use { oldDatabase ->
            oldDatabase.execSQL(
                "CREATE TABLE app_preferences (`key` TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(`key`))",
            )
            oldDatabase.execSQL("INSERT INTO app_preferences (`key`, enabled) VALUES ('theme', 1)")
            oldDatabase.version = 1
        }

        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()

        assertEquals(AppPreferenceEntity("theme", true), database.appPreferenceDao().observe("theme").first())
        assertEquals(null, database.scanStateDao().get())
        assertEquals(0, database.offerDao().count())
        database.close()
        context.deleteDatabase(name)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun migrationFromVersionTwoPreservesMetadataAndAllowsUnknownTitle() = runBlocking {
        database.close()
        val name = "migration-v2-test.db"
        context.deleteDatabase(name)
        val oldFile: File = context.getDatabasePath(name)
        oldFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(oldFile, null).use { oldDatabase ->
            oldDatabase.execSQL(
                """CREATE TABLE offers (
                    sourceSmsId INTEGER NOT NULL, offerTitle TEXT NOT NULL, merchantName TEXT,
                    discountCode TEXT, discountValue TEXT, validUntilMillis INTEGER,
                    sourceDateMillis INTEGER NOT NULL, extractedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(sourceSmsId)
                )""".trimIndent(),
            )
            oldDatabase.execSQL(
                "INSERT INTO offers VALUES (5, 'فروش بهاره', 'آریا', 'Spring5', '15%', NULL, 10, 20)",
            )
            oldDatabase.execSQL(
                """CREATE TABLE scan_state (
                    scanKey TEXT NOT NULL, lastScannedSmsDateMillis INTEGER NOT NULL,
                    lastScannedSmsId INTEGER NOT NULL, lastSuccessfulScanAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(scanKey)
                )""".trimIndent(),
            )
            oldDatabase.execSQL(
                """CREATE TABLE app_preferences (
                    `key` TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(`key`)
                )""".trimIndent(),
            )
            oldDatabase.version = 2
        }

        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val migrated = database.offerDao().observeAll().first().single()
        assertEquals("فروش بهاره", migrated.offerTitle)
        assertEquals("Spring5", migrated.discountCode)
        assertEquals("15%", migrated.discountValue)
        assertEquals(null, migrated.percentageDiscounts)
        assertEquals(listOf(OfferCategory.UNKNOWN), migrated.applicableCategories)

        database.offerDao().upsertAll(listOf(offer(6L, title = null)))
        assertEquals(null, database.offerDao().observeAll().first().first { it.sourceSmsId == 6L }.offerTitle)
        database.close()
        context.deleteDatabase(name)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun migrationFromVersionFourMapsLegacyCategoryAndAddsCorrectionColumn() = runBlocking {
        database.close()
        val name = "migration-v4-test.db"
        context.deleteDatabase(name)
        val oldFile = context.getDatabasePath(name)
        oldFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(oldFile, null).use { oldDatabase ->
            oldDatabase.execSQL(
                """CREATE TABLE offers (
                    sourceSmsId INTEGER NOT NULL, offerTitle TEXT, merchantName TEXT,
                    discountCode TEXT, discountValue TEXT, percentageDiscounts TEXT,
                    fixedDiscounts TEXT, freeShipping INTEGER, minimumPurchase TEXT,
                    productCategory TEXT, extractionConfidence REAL NOT NULL,
                    validUntilMillis INTEGER, sourceDateMillis INTEGER NOT NULL,
                    extractedAtMillis INTEGER NOT NULL, PRIMARY KEY(sourceSmsId)
                )""".trimIndent(),
            )
            oldDatabase.execSQL(
                """INSERT INTO offers (
                    sourceSmsId, offerTitle, merchantName, productCategory,
                    extractionConfidence, sourceDateMillis, extractedAtMillis
                ) VALUES (9, 'پیشنهاد', 'دیجی‌کالا', 'Electronics', 0.9, 100, 200)""".trimIndent(),
            )
            oldDatabase.execSQL(
                "CREATE INDEX index_offers_sourceDateMillis_sourceSmsId ON offers (sourceDateMillis, sourceSmsId)",
            )
            oldDatabase.execSQL(
                "CREATE TABLE scan_state (scanKey TEXT NOT NULL, lastScannedSmsDateMillis INTEGER NOT NULL, lastScannedSmsId INTEGER NOT NULL, lastSuccessfulScanAtMillis INTEGER NOT NULL, PRIMARY KEY(scanKey))",
            )
            oldDatabase.execSQL("CREATE TABLE app_preferences (`key` TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(`key`))")
            oldDatabase.version = 4
        }

        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val migrated = database.offerDao().observeAll().first().single()
        assertEquals(listOf(OfferCategory.Electronics), migrated.applicableCategories)
        assertEquals(null, migrated.merchantCorrection)
        assertEquals(null, migrated.campaignBrand)
        database.close()
        context.deleteDatabase(name)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun offer(sourceSmsId: Long, title: String?) = OfferEntity(
        sourceSmsId = sourceSmsId,
        offerTitle = title,
        merchantName = null,
        discountCode = null,
        discountValue = null,
        percentageDiscounts = null,
        fixedDiscounts = null,
        freeShipping = null,
        minimumPurchase = null,
        productCategory = null,
        extractionConfidence = 0.0,
        validUntilMillis = null,
        sourceDateMillis = 100L,
        extractedAtMillis = 200L,
    )
}
