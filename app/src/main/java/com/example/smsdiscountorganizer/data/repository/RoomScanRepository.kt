package com.example.smsdiscountorganizer.data.repository

import androidx.room.withTransaction
import com.example.smsdiscountorganizer.data.local.AppDatabase
import com.example.smsdiscountorganizer.data.local.ScanStateEntity
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.scan.ScanRepository
import com.example.smsdiscountorganizer.domain.scan.ScanState
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomScanRepository @Inject constructor(
    private val database: AppDatabase,
) : ScanRepository {
    override fun observeScanState(): Flow<ScanState?> =
        database.scanStateDao().observe().map { it?.toDomain() }

    override suspend fun getScanState(): ScanState? = database.scanStateDao().get()?.toDomain()

    override suspend fun commitScan(offers: List<Offer>, state: ScanState) {
        database.withTransaction {
            database.offerDao().upsertAll(offers.map(Offer::toEntity))
            database.scanStateDao().upsert(state.toEntity())
        }
    }
}

private const val NO_SMS_ID = -1L
private const val NO_SMS_DATE = -1L

private fun ScanStateEntity.toDomain(): ScanState = ScanState(
    cursor = if (lastScannedSmsDateMillis == NO_SMS_DATE && lastScannedSmsId == NO_SMS_ID) {
        null
    } else {
        SmsCursor(lastScannedSmsDateMillis, lastScannedSmsId)
    },
    lastSuccessfulScanAtMillis = lastSuccessfulScanAtMillis,
)

private fun ScanState.toEntity(): ScanStateEntity = ScanStateEntity(
    scanKey = INBOX_SCAN_KEY,
    lastScannedSmsDateMillis = cursor?.dateMillis ?: NO_SMS_DATE,
    lastScannedSmsId = cursor?.smsId ?: NO_SMS_ID,
    lastSuccessfulScanAtMillis = lastSuccessfulScanAtMillis,
)

private const val INBOX_SCAN_KEY = "inbox"
