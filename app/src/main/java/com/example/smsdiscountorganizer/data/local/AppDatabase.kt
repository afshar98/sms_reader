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

    @Query("DELETE FROM app_preferences")
    suspend fun clearAll()
}

@Database(
    entities = [AppPreferenceEntity::class, OfferEntity::class, ScanStateEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao

    abstract fun offerDao(): OfferDao

    abstract fun scanStateDao(): ScanStateDao

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
    }
}
