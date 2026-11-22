package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.cursor
import com.example.smsdiscountorganizer.engine.OfferExtractor
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ScanSmsUseCase @Inject constructor(
    private val smsDataSource: SmsDataSource,
    private val offerExtractor: OfferExtractor,
    private val scanRepository: ScanRepository,
    private val clock: Clock,
) {
    suspend fun scan(): ScanResult {
        val previousState = try {
            scanRepository.getScanState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return ScanResult.Failure(ScanResult.Reason.PersistenceFailed)
        }
        val previousCursor = previousState?.cursor
        val messages = try {
            val inbox = smsDataSource.readInbox(previousCursor)
            withContext(Dispatchers.Default) {
                inbox.asSequence()
                    .filter { previousCursor == null || it.cursor > previousCursor }
                    .distinctBy { it.id }
                    .sortedBy { it.cursor }
                    .toList()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return ScanResult.Failure(ScanResult.Reason.InboxReadFailed)
        }

        val completedAtMillis = clock.millis()
        val offers: List<Offer> = try {
            withContext(Dispatchers.Default) {
                val coroutineContext = currentCoroutineContext()
                buildList {
                    messages.forEach { message ->
                        coroutineContext.ensureActive()
                        offerExtractor.extract(message)?.let { metadata ->
                            add(
                                Offer(
                                    sourceSmsId = message.id,
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
                                    sourceDateMillis = message.dateMillis,
                                    extractedAtMillis = completedAtMillis,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return ScanResult.Failure(ScanResult.Reason.OfferExtractionFailed)
        }

        val nextCursor = messages.lastOrNull()?.cursor ?: previousCursor
        return try {
            scanRepository.commitScan(
                offers = offers,
                state = ScanState(nextCursor, completedAtMillis),
            )
            ScanResult.Success(messages.size, offers.size, completedAtMillis)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ScanResult.Failure(ScanResult.Reason.PersistenceFailed)
        }
    }
}
