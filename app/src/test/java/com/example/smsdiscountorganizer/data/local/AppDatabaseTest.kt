package com.example.smsdiscountorganizer.data.local

import android.database.sqlite.SQLiteDatabase
import android.app.Application
import androidx.room.Room
import com.example.smsdiscountorganizer.data.repository.RoomOfferRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun clearingLocalDataRemovesOffersPreferencesAndScanState() = runBlocking {
        database.offerDao().upsertAll(listOf(offer(44L, "Ten percent")))
        database.appPreferenceDao().save(AppPreferenceEntity("theme", true))
        database.scanStateDao().upsert(ScanStateEntity("inbox", 100L, 44L, 200L))

        RoomOfferRepository(database).clearAllLocalData()

        assertEquals(0, database.offerDao().count())
        assertEquals(null, database.appPreferenceDao().observe("theme").first())
        assertEquals(null, database.scanStateDao().get())
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
            .addMigrations(AppDatabase.MIGRATION_1_2)
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

    private fun offer(sourceSmsId: Long, title: String) = OfferEntity(
        sourceSmsId = sourceSmsId,
        offerTitle = title,
        merchantName = null,
        discountCode = null,
        discountValue = null,
        validUntilMillis = null,
        sourceDateMillis = 100L,
        extractedAtMillis = 200L,
    )
}
