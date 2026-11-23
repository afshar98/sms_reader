package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import kotlinx.coroutines.flow.Flow

interface ScanRepository {
    fun observeScanState(): Flow<ScanState?>

    suspend fun getScanState(): ScanState?

    /** Atomically replaces the derived, body-free sender snapshot and its fingerprint. */
    suspend fun replaceSenderProfiles(profiles: List<SenderMerchantProfile>, fingerprint: String)

    suspend fun getSenderProfiles(): List<SenderMerchantProfile>

    /** Returns body-free processing fingerprints in the selected provider-date window. */
    suspend fun getProcessedSms(dateRange: SmsDateRange?): List<ProcessedSmsRecord> = emptyList()

    /** Persists offers and the next checkpoint in one database transaction. */
    suspend fun commitScan(
        offers: List<Offer>,
        state: ScanState,
        processedMessages: List<ProcessedSmsRecord> = emptyList(),
        deleteOfferIds: List<Long> = emptyList(),
        reconciliation: ScanReconciliation? = null,
    )
}
