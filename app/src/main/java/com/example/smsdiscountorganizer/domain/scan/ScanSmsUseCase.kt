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
import java.time.ZoneId
import java.security.MessageDigest
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
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val scanMutex = Mutex()
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    suspend fun scan(
        fullRescan: Boolean = false,
        selection: SmsDateRangeSelection = SmsDateRangeSelection(),
    ): ScanResult = scanMutex.withLock {
        _progress.value = ScanProgress(
            isScanning = true,
            isFullRescan = fullRescan,
            phase = ScanPhase.READING,
        )
        try {
            scanInbox(fullRescan, selection)
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

    private suspend fun scanInbox(fullRescan: Boolean, selection: SmsDateRangeSelection): ScanResult {
        val resolution = selection.resolve(clock, zoneId) as? SmsDateRangeResolution.Valid
            ?: return fail(ScanResult.Reason.InvalidDateRange)
        val dateRange = resolution.range
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
        val hasPersistedProfileSnapshot = storedState?.senderProfileFingerprint?.isNotBlank() == true
        val previousProfiles = if (storedCursor != null && hasPersistedProfileSnapshot) {
            try {
                scanRepository.getSenderProfiles()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return fail(ScanResult.Reason.PersistenceFailed)
            }
        } else {
            emptyList()
        }
        val canBuildProfilesIncrementally = !fullRescan && storedCursor != null && hasPersistedProfileSnapshot
        if (canBuildProfilesIncrementally) profileBuilder.seed(previousProfiles)
        var profiledMessages = 0
        _progress.value = _progress.value.copy(phase = ScanPhase.PROFILING)
        try {
            if (fullRescan && hasPersistedProfileSnapshot) {
                // A full offer rescan must not replace the broader, previously learned sender
                // snapshot with evidence from only the currently selected date window.
                profiledMessages = 0
            } else {
            withContext(Dispatchers.Default) {
                smsDataSource.readInbox(after = storedCursor.takeIf { canBuildProfilesIncrementally }, dateRange = dateRange).collect { message ->
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
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return fail(ScanResult.Reason.PermissionDenied)
        } catch (_: Exception) {
            return fail(ScanResult.Reason.InboxReadFailed)
        }
        _progress.value = _progress.value.copy(profiledMessages = profiledMessages)

        val senderProfiles = if (fullRescan && hasPersistedProfileSnapshot) previousProfiles else profileBuilder.build()
        val profileFingerprint = SenderMerchantProfileBuilder.profileFingerprint(senderProfiles)
        val previousOfferProfileFingerprint = storedState?.offersSenderProfileFingerprint.orEmpty()
        val resolutionFingerprint = senderResolutionFingerprint(senderProfiles)
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
        val profileResolutionChanged =
            profileResolutionSignature(previousProfiles) != profileResolutionSignature(senderProfiles)
        val profileSnapshotMissing = storedState != null && !hasPersistedProfileSnapshot
        val offersWereProcessedAgainstStaleResolution = storedState != null &&
            storedState.offersSenderResolutionFingerprint != resolutionFingerprint
        val reprocessAll = fullRescan || storedState == null || profileSnapshotMissing ||
            profileResolutionChanged || offersWereProcessedAgainstStaleResolution
        if (reprocessAll && previousOfferProfileFingerprint == profileFingerprint) {
            try {
                scanRepository.commitScan(
                    offers = emptyList(),
                    state = ScanState(
                        cursor = storedCursor,
                        lastSuccessfulScanAtMillis = lastSuccessfulScanAt,
                        offersSenderProfileFingerprint = "",
                        senderProfileFingerprint = profileFingerprint,
                        offersSenderResolutionFingerprint = "",
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return fail(ScanResult.Reason.PersistenceFailed)
            }
        }
        // A provider-ID high-water mark misses imported rows with older IDs and edits to existing
        // messages. Revisit the selected date window and skip unchanged rows by body-free hash.
        val startingCursor: SmsCursor? = null
        val previousProcessedMessages = try {
            scanRepository.getProcessedSms(dateRange).associateBy(ProcessedSmsRecord::smsId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return fail(ScanResult.Reason.PersistenceFailed)
        }
        val profilesBySender = senderProfiles.associateBy(SenderMerchantProfile::senderFingerprint)
        var lastCursor = startingCursor
        var lastSeenSmsId = startingCursor?.smsId
        var processedMessages = 0
        var offersFound = 0
        var malformedMessages = 0
        var unchangedMessages = 0
        val observedSmsIds = LinkedHashSet<Long>()
        var batchMessageCount = 0
        var lastProgressUpdateNanos = 0L
        val extractedAtMillis = clock.millis()
        val batchOffers = ArrayList<Offer>(SCAN_BATCH_SIZE)
        val batchProcessedMessages = ArrayList<ProcessedSmsRecord>(SCAN_BATCH_SIZE)
        val batchDeleteOfferIds = ArrayList<Long>()

        suspend fun saveBatch() {
            if (batchMessageCount == 0) return
            _progress.value = _progress.value.copy(
                phase = ScanPhase.SAVING,
                processedMessages = processedMessages,
                offersFound = offersFound,
                malformedMessages = malformedMessages,
            )
            val checkpointCursor = if (fullRescan) storedCursor else lastCursor
            try {
                scanRepository.commitScan(
                    offers = batchOffers.toList(),
                    state = ScanState(
                        cursor = checkpointCursor,
                        lastSuccessfulScanAtMillis = lastSuccessfulScanAt,
                        offersSenderProfileFingerprint = if (reprocessAll) "" else previousOfferProfileFingerprint,
                        senderProfileFingerprint = profileFingerprint,
                        offersSenderResolutionFingerprint = if (reprocessAll) "" else storedState.offersSenderResolutionFingerprint,
                    ),
                    processedMessages = batchProcessedMessages.toList(),
                    deleteOfferIds = batchDeleteOfferIds.toList(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                throw ScanPersistenceException()
            }
            batchOffers.clear()
            batchProcessedMessages.clear()
            batchDeleteOfferIds.clear()
            batchMessageCount = 0
            _progress.value = _progress.value.copy(phase = ScanPhase.PROCESSING)
        }

        try {
            withContext(Dispatchers.Default) {
                smsDataSource.readInbox(after = startingCursor, dateRange = dateRange).collect { message ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (startingCursor != null && message.cursor <= startingCursor) return@collect
                    val priorSmsId = lastSeenSmsId
                    if (priorSmsId != null && message.id <= priorSmsId) return@collect
                    lastSeenSmsId = message.id
                    lastCursor = message.cursor
                    observedSmsIds += message.id
                    processedMessages += 1
                    val contentFingerprint = contentFingerprint(message)
                    val previous = previousProcessedMessages[message.id]
                    val unchanged = !fullRescan && !reprocessAll &&
                        previous?.pipelineVersion == PROCESSING_PIPELINE_VERSION &&
                        previous.contentFingerprint == contentFingerprint
                    if (unchanged) {
                        unchangedMessages += 1
                    } else {
                        batchMessageCount += 1
                        var producedOffer = false
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
                                producedOffer = true
                            }
                        }
                        if (!producedOffer && previous?.producedOffer == true) batchDeleteOfferIds += message.id
                        batchProcessedMessages += ProcessedSmsRecord(
                            smsId = message.id,
                            sourceDateMillis = message.dateMillis,
                            contentFingerprint = contentFingerprint,
                            pipelineVersion = PROCESSING_PIPELINE_VERSION,
                            producedOffer = producedOffer,
                        )
                    }

                    val nowNanos = System.nanoTime()
                    if (processedMessages == 1 ||
                        processedMessages % PROGRESS_MESSAGE_INTERVAL == 0 ||
                        nowNanos - lastProgressUpdateNanos >= PROGRESS_TIME_INTERVAL_NANOS
                    ) {
                        _progress.value = _progress.value.copy(
                            phase = ScanPhase.PROCESSING,
                            processedMessages = processedMessages,
                            offersFound = offersFound,
                            malformedMessages = malformedMessages,
                        )
                        lastProgressUpdateNanos = nowNanos
                    }
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
                    offersSenderResolutionFingerprint = resolutionFingerprint,
                ),
                reconciliation = ScanReconciliation(dateRange = dateRange, observedSmsIds = observedSmsIds),
            )
            _progress.value = _progress.value.copy(isScanning = false, phase = ScanPhase.COMPLETED)
            return ScanResult.Success(
                messagesScanned = processedMessages,
                offersUpserted = offersFound,
                completedAtMillis = completedAtMillis,
                malformedMessages = malformedMessages,
                messagesUnchanged = unchangedMessages,
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

    /** Offers depend on the resolved merchant name; evidence count/confidence changes alone do not alter them. */
    private fun profileResolutionSignature(
        profiles: List<SenderMerchantProfile>,
    ): List<Pair<String, String?>> =
        profiles.asSequence()
            .mapNotNull { profile -> profile.resolvedMerchantName?.let { profile.senderFingerprint to it } }
            .sortedBy { it.first }
            .toList()

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

    private fun contentFingerprint(message: SmsMessage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(message.address.orEmpty().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(message.body.orEmpty().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(message.dateMillis.toString().toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun senderResolutionFingerprint(profiles: List<SenderMerchantProfile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        profiles.asSequence()
            .filter { it.resolvedMerchantName != null }
            .sortedBy(SenderMerchantProfile::senderFingerprint)
            .forEach { profile ->
                digest.update(profile.senderFingerprint.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(profile.resolvedMerchantName.orEmpty().toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal const val SCAN_BATCH_SIZE = 50
private const val PROGRESS_MESSAGE_INTERVAL = 100
private const val PROGRESS_TIME_INTERVAL_NANOS = 150_000_000L
private const val PROCESSING_PIPELINE_VERSION = 1
