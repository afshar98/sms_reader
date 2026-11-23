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

    /** Persists offers and the next checkpoint in one database transaction. */
    suspend fun commitScan(offers: List<Offer>, state: ScanState)
}
