package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import kotlinx.coroutines.flow.Flow

interface ScanRepository {
    fun observeScanState(): Flow<ScanState?>

    suspend fun getScanState(): ScanState?

    /** Persists offers and the next checkpoint in one database transaction. */
    suspend fun commitScan(offers: List<Offer>, state: ScanState)
}
