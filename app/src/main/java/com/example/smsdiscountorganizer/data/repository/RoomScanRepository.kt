package com.example.smsdiscountorganizer.data.repository

import androidx.room.withTransaction
import com.example.smsdiscountorganizer.data.local.AppDatabase
import com.example.smsdiscountorganizer.data.local.ScanStateEntity
import com.example.smsdiscountorganizer.data.local.SenderMerchantProfileEntity
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
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

    override suspend fun replaceSenderProfiles(profiles: List<SenderMerchantProfile>, fingerprint: String) {
        database.withTransaction {
            val profileDao = database.senderMerchantProfileDao()
            profileDao.clear()
            profileDao.insertAll(profiles.flatMap(SenderMerchantProfile::toEntities))
            val scanState = database.scanStateDao().get()
                ?: ScanStateEntity(INBOX_SCAN_KEY, NO_SMS_DATE, NO_SMS_ID, 0L)
            database.scanStateDao().upsert(scanState.copy(senderProfileFingerprint = fingerprint))
        }
    }

    override suspend fun getSenderProfiles(): List<SenderMerchantProfile> =
        database.senderMerchantProfileDao().getAll()
            .groupBy(SenderMerchantProfileEntity::senderFingerprint)
            .map { (sender, rows) ->
                val first = rows.first()
                SenderMerchantProfile(
                    senderFingerprint = sender,
                    merchantSupport = rows.associate { it.merchantName to it.supportingMessages },
                    totalSupportingMessages = first.totalSupportingMessages,
                    isConflicted = first.isConflicted,
                    isSharedGateway = first.isSharedGateway,
                    confidence = first.confidence,
                    resolvedMerchantName = first.resolvedMerchantName,
                )
            }

    override suspend fun commitScan(offers: List<Offer>, state: ScanState) {
        database.withTransaction {
            val offerDao = database.offerDao()
            offerDao.upsertAll(offers.map { offer ->
                val entity = offer.toEntity()
                entity.copy(merchantCorrection = entity.merchantCorrection ?: offerDao.getBySourceSmsId(offer.sourceSmsId)?.merchantCorrection)
            })
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
    offersSenderProfileFingerprint = offersSenderProfileFingerprint,
    senderProfileFingerprint = senderProfileFingerprint,
)

private fun ScanState.toEntity(): ScanStateEntity = ScanStateEntity(
    scanKey = INBOX_SCAN_KEY,
    lastScannedSmsDateMillis = cursor?.dateMillis ?: NO_SMS_DATE,
    lastScannedSmsId = cursor?.smsId ?: NO_SMS_ID,
    lastSuccessfulScanAtMillis = lastSuccessfulScanAtMillis,
    senderProfileFingerprint = senderProfileFingerprint,
    offersSenderProfileFingerprint = offersSenderProfileFingerprint,
)

private fun SenderMerchantProfile.toEntities(): List<SenderMerchantProfileEntity> = merchantSupport.map { (merchant, count) ->
    SenderMerchantProfileEntity(
        senderFingerprint = senderFingerprint,
        merchantName = merchant,
        supportingMessages = count,
        totalSupportingMessages = totalSupportingMessages,
        isConflicted = isConflicted,
        isSharedGateway = isSharedGateway,
        confidence = confidence,
        resolvedMerchantName = resolvedMerchantName,
    )
}

private const val INBOX_SCAN_KEY = "inbox"
