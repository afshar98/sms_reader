package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.domain.sms.cursor
import com.example.smsdiscountorganizer.engine.OfferExtractor
import com.example.smsdiscountorganizer.engine.SenderIdNormalizer
import com.example.smsdiscountorganizer.engine.SenderMerchantProfileBuilder
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ScanSmsUseCase @Inject constructor(
    private val smsDataSource: SmsDataSource,
    private val offerExtractor: OfferExtractor,
    private val scanRepository: ScanRepository,
    private val clock: Clock,
) {
    private val scanMutex = Mutex()
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    suspend fun scan(fullRescan: Boolean = false): ScanResult = scanMutex.withLock {
        _progress.value = ScanProgress(
            isScanning = true,
            isFullRescan = fullRescan,
            phase = ScanPhase.READING,
        )
        try {
            scanInbox(fullRescan)
        } catch (cancellation: CancellationException) {
            _progress.value = _progress.value.copy(isScanning = false, phase = ScanPhase.CANCELLED)
            throw cancellation
        } catch (_: Exception) {
            _progress.value = _progress.value.copy(isScanning = false, phase = ScanPhase.FAILED)
            ScanResult.Failure(ScanResult.Reason.InboxReadFailed)
        } finally {
            if (_progress.value.isScanning) {
                _progress.value = _progress.value.copy(isScanning = false)
            }
        }
    }

    private suspend fun scanInbox(fullRescan: Boolean): ScanResult {
        val storedState = try {
            scanRepository.getScanState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return fail(ScanResult.Reason.PersistenceFailed)
        }
        val storedCursor = storedState?.cursor
        val lastSuccessfulScanAt = storedState?.lastSuccessfulScanAtMillis ?: 0L
        val profileBuilder = SenderMerchantProfileBuilder()
        var profiledMessages = 0
        _progress.value = _progress.value.copy(phase = ScanPhase.PROFILING)
        try {
            withContext(Dispatchers.Default) {
                smsDataSource.readInbox(after = null).collect { message ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    profiledMessages += 1
                    try {
                        profileBuilder.add(message)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // A malformed Inbox row contributes no merchant evidence.
                    }
                    if (profiledMessages % SCAN_BATCH_SIZE == 0) {
                        _progress.value = _progress.value.copy(profiledMessages = profiledMessages)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return fail(ScanResult.Reason.PermissionDenied)
        } catch (_: Exception) {
            return fail(ScanResult.Reason.InboxReadFailed)
        }
        _progress.value = _progress.value.copy(profiledMessages = profiledMessages)

        val senderProfiles = profileBuilder.build()
        val profileFingerprint = profileBuilder.fingerprint()
        val previousOfferProfileFingerprint = storedState?.offersSenderProfileFingerprint.orEmpty()
        val profilesChanged = storedState?.senderProfileFingerprint != profileFingerprint
        if (profilesChanged) {
            try {
                scanRepository.replaceSenderProfiles(senderProfiles, profileFingerprint)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return fail(ScanResult.Reason.PersistenceFailed)
            }
        }
        val reprocessAll = fullRescan || profilesChanged || previousOfferProfileFingerprint != profileFingerprint
        if (reprocessAll && previousOfferProfileFingerprint == profileFingerprint) {
            try {
                scanRepository.commitScan(
                    offers = emptyList(),
                    state = ScanState(
                        cursor = storedCursor,
                        lastSuccessfulScanAtMillis = lastSuccessfulScanAt,
                        offersSenderProfileFingerprint = "",
                        senderProfileFingerprint = profileFingerprint,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return fail(ScanResult.Reason.PersistenceFailed)
            }
        }
        val startingCursor = if (reprocessAll) null else storedCursor
        val profilesBySender = senderProfiles.associateBy(SenderMerchantProfile::senderFingerprint)
        var lastCursor = startingCursor
        var lastSeenSmsId = startingCursor?.smsId
        var processedMessages = 0
        var offersFound = 0
        var malformedMessages = 0
        var batchMessageCount = 0
        val extractedAtMillis = clock.millis()
        val batchOffers = ArrayList<Offer>(SCAN_BATCH_SIZE)

        suspend fun saveBatch() {
            if (batchMessageCount == 0) return
            _progress.value = _progress.value.copy(phase = ScanPhase.SAVING)
            val checkpointCursor = if (fullRescan) storedCursor else lastCursor
            try {
                scanRepository.commitScan(
                    offers = batchOffers.toList(),
                    state = ScanState(
                        cursor = checkpointCursor,
                        lastSuccessfulScanAtMillis = lastSuccessfulScanAt,
                        offersSenderProfileFingerprint = if (reprocessAll) "" else previousOfferProfileFingerprint,
                        senderProfileFingerprint = profileFingerprint,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                throw ScanPersistenceException()
            }
            batchOffers.clear()
            batchMessageCount = 0
            _progress.value = _progress.value.copy(phase = ScanPhase.PROCESSING)
        }

        try {
            withContext(Dispatchers.Default) {
                smsDataSource.readInbox(startingCursor).collect { message ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (startingCursor != null && message.cursor <= startingCursor) return@collect
                    val priorSmsId = lastSeenSmsId
                    if (priorSmsId != null && message.id <= priorSmsId) return@collect
                    lastSeenSmsId = message.id
                    lastCursor = message.cursor
                    processedMessages += 1
                    batchMessageCount += 1

                    val body = message.body
                    if (body.isNullOrBlank()) {
                        malformedMessages += 1
                    } else {
                        val metadata = try {
                            val fingerprint = SenderIdNormalizer.fingerprint(message.address)
                            offerExtractor.extract(message, fingerprint?.let(profilesBySender::get))
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            malformedMessages += 1
                            null
                        }
                        if (metadata != null) {
                            batchOffers += message.toOffer(metadata, extractedAtMillis)
                            offersFound += 1
                        }
                    }

                    _progress.value = _progress.value.copy(
                        phase = ScanPhase.PROCESSING,
                        processedMessages = processedMessages,
                        offersFound = offersFound,
                        malformedMessages = malformedMessages,
                    )
                    if (batchMessageCount >= SCAN_BATCH_SIZE) saveBatch()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return fail(ScanResult.Reason.PermissionDenied)
        } catch (_: ScanPersistenceException) {
            return fail(ScanResult.Reason.PersistenceFailed)
        } catch (_: Exception) {
            return fail(ScanResult.Reason.InboxReadFailed)
        }

        try {
            saveBatch()
            _progress.value = _progress.value.copy(phase = ScanPhase.SAVING)
            val completedAtMillis = clock.millis()
            scanRepository.commitScan(
                offers = emptyList(),
                state = ScanState(
                    cursor = lastCursor,
                    lastSuccessfulScanAtMillis = completedAtMillis,
                    offersSenderProfileFingerprint = profileFingerprint,
                    senderProfileFingerprint = profileFingerprint,
                ),
            )
            _progress.value = _progress.value.copy(isScanning = false, phase = ScanPhase.COMPLETED)
            return ScanResult.Success(
                messagesScanned = processedMessages,
                offersUpserted = offersFound,
                completedAtMillis = completedAtMillis,
                malformedMessages = malformedMessages,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return fail(ScanResult.Reason.PersistenceFailed)
        }
    }

    private fun fail(reason: ScanResult.Reason): ScanResult.Failure {
        _progress.value = _progress.value.copy(isScanning = false, phase = ScanPhase.FAILED)
        return ScanResult.Failure(reason)
    }

    private fun SmsMessage.toOffer(
        metadata: com.example.smsdiscountorganizer.domain.offer.OfferMetadata,
        extractedAtMillis: Long,
    ): Offer = Offer(
        sourceSmsId = id,
        title = metadata.offerTitle,
        merchantName = metadata.merchantName,
        discountCode = metadata.couponCode,
        discountValue = metadata.percentageDiscounts?.joinToString(",") { "$it%" },
        percentageDiscounts = metadata.percentageDiscounts,
        fixedDiscounts = metadata.fixedDiscounts,
        freeShipping = metadata.freeShipping,
        minimumPurchase = metadata.minimumPurchase,
        productCategory = metadata.productCategory,
        extractionConfidence = metadata.confidence,
        validUntilMillis = metadata.validUntilMillis,
        sourceDateMillis = dateMillis,
        extractedAtMillis = extractedAtMillis,
        applicableCategories = metadata.applicableCategories,
        campaignBrand = metadata.campaignBrand,
    )

    private class ScanPersistenceException : Exception()
}

internal const val SCAN_BATCH_SIZE = 50
