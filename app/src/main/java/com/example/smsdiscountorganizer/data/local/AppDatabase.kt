package com.example.smsdiscountorganizer.data.local

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean,
)

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<AppPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(preference: AppPreferenceEntity)

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM app_preferences")
    suspend fun clearAll()
}

@Database(
    entities = [AppPreferenceEntity::class, OfferEntity::class, ScanStateEntity::class, SenderMerchantProfileEntity::class, ProcessedSmsEntity::class],
    version = 9,
    exportSchema = false,
)
@TypeConverters(OfferConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao

    abstract fun offerDao(): OfferDao

    abstract fun scanStateDao(): ScanStateDao

    abstract fun senderMerchantProfileDao(): SenderMerchantProfileDao

    abstract fun processedSmsDao(): ProcessedSmsDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS offers (
                        sourceSmsId INTEGER NOT NULL,
                        offerTitle TEXT NOT NULL,
                        merchantName TEXT,
                        discountCode TEXT,
                        discountValue TEXT,
                        validUntilMillis INTEGER,
                        sourceDateMillis INTEGER NOT NULL,
                        extractedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(sourceSmsId)
                    )""".trimIndent(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS scan_state (
                        scanKey TEXT NOT NULL,
                        lastScannedSmsDateMillis INTEGER NOT NULL,
                        lastScannedSmsId INTEGER NOT NULL,
                        lastSuccessfulScanAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(scanKey)
                    )""".trimIndent(),
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE offers_new (
                        sourceSmsId INTEGER NOT NULL,
                        offerTitle TEXT,
                        merchantName TEXT,
                        discountCode TEXT,
                        discountValue TEXT,
                        percentageDiscounts TEXT,
                        fixedDiscounts TEXT,
                        freeShipping INTEGER,
                        minimumPurchase TEXT,
                        productCategory TEXT,
                        extractionConfidence REAL NOT NULL,
                        validUntilMillis INTEGER,
                        sourceDateMillis INTEGER NOT NULL,
                        extractedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(sourceSmsId)
                    )""".trimIndent(),
                )
                db.execSQL(
                    """INSERT INTO offers_new (
                        sourceSmsId, offerTitle, merchantName, discountCode, discountValue,
                        extractionConfidence, validUntilMillis, sourceDateMillis, extractedAtMillis
                    ) SELECT sourceSmsId, offerTitle, merchantName, discountCode, discountValue,
                        0.0, validUntilMillis, sourceDateMillis, extractedAtMillis FROM offers""".trimIndent(),
                )
                db.execSQL("DROP TABLE offers")
                db.execSQL("ALTER TABLE offers_new RENAME TO offers")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_offers_sourceDateMillis_sourceSmsId " +
                        "ON offers (sourceDateMillis, sourceSmsId)",
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN applicableCategories TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE offers ADD COLUMN merchantCorrection TEXT")
                db.execSQL(
                    """UPDATE offers SET applicableCategories = CASE productCategory
                        WHEN 'Apparel' THEN 'apparel'
                        WHEN 'Grocery' THEN 'grocery'
                        WHEN 'Electronics' THEN 'electronics'
                        WHEN 'Beauty' THEN 'beauty'
                        WHEN 'Food' THEN 'food'
                        WHEN 'Home' THEN 'home'
                        WHEN 'Travel' THEN 'travel'
                        WHEN 'Entertainment' THEN 'entertainment'
                        ELSE 'unknown'
                    END""".trimIndent(),
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_state ADD COLUMN senderProfileFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scan_state ADD COLUMN offersSenderProfileFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sender_merchant_profiles (
                        senderFingerprint TEXT NOT NULL,
                        merchantName TEXT NOT NULL,
                        supportingMessages INTEGER NOT NULL,
                        totalSupportingMessages INTEGER NOT NULL,
                        isConflicted INTEGER NOT NULL,
                        isSharedGateway INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        resolvedMerchantName TEXT,
                        PRIMARY KEY(senderFingerprint, merchantName)
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sender_merchant_profiles_senderFingerprint " +
                        "ON sender_merchant_profiles (senderFingerprint)",
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN campaignBrand TEXT")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS processed_sms (
                        smsId INTEGER NOT NULL,
                        sourceDateMillis INTEGER NOT NULL,
                        contentFingerprint TEXT NOT NULL,
                        pipelineVersion INTEGER NOT NULL,
                        producedOffer INTEGER NOT NULL,
                        PRIMARY KEY(smsId)
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_processed_sms_sourceDateMillis ON processed_sms (sourceDateMillis)",
                )
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_state ADD COLUMN offersSenderResolutionFingerprint TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
