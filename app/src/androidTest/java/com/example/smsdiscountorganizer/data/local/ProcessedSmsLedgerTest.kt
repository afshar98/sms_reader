package com.example.smsdiscountorganizer.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsdiscountorganizer.data.repository.RoomScanRepository
import com.example.smsdiscountorganizer.domain.scan.ProcessedSmsRecord
import com.example.smsdiscountorganizer.domain.scan.ScanState
import com.example.smsdiscountorganizer.domain.scan.SmsDateRange
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an in-memory Room database and synthetic hashes; it never queries the SMS provider. */
@RunWith(AndroidJUnit4::class)
class ProcessedSmsLedgerTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomScanRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            AppDatabase::class.java,
        ).build()
        repository = RoomScanRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ledgerUpsertUpdatesMetadataAndRangeQueryDoesNotStoreBodies() = runBlocking {
        val initial = ProcessedSmsRecord(1L, 100L, "a".repeat(64), 1, true)
        repository.commitScan(emptyList(), ScanState(null, 0L), listOf(initial))
        val updated = initial.copy(contentFingerprint = "b".repeat(64), producedOffer = false)
        repository.commitScan(emptyList(), ScanState(null, 1L), listOf(updated))
        repository.commitScan(emptyList(), ScanState(null, 2L), listOf(updated.copy(smsId = 2L, sourceDateMillis = 500L)))

        assertEquals(listOf(updated), repository.getProcessedSms(SmsDateRange(0L, 200L)))
        val columns = database.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA table_info(processed_sms)")).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertTrue("body" !in columns.map(String::lowercase))
        assertTrue("address" !in columns.map(String::lowercase))
    }
}
