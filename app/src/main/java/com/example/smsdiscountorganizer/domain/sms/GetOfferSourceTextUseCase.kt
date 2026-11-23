package com.example.smsdiscountorganizer.domain.sms

import com.example.smsdiscountorganizer.engine.classification.ClassificationCategory
import com.example.smsdiscountorganizer.engine.classification.DiscountClassifier
import javax.inject.Inject

/** Reads source text transiently for an offer detail; never stores or logs the body. */
class GetOfferSourceTextUseCase @Inject constructor(
    private val smsDataSource: SmsDataSource,
) {
    private val classifier = DiscountClassifier()

    suspend operator fun invoke(sourceSmsId: Long): String? {
        val message = try {
            smsDataSource.readMessageById(sourceSmsId)
        } catch (_: SecurityException) {
            return null
        } catch (_: SmsInboxUnavailableException) {
            return null
        }
        if (message?.id != sourceSmsId) return null

        val originalText = message.body ?: return null
        return originalText.takeIf {
            classifier.classify(it).category == ClassificationCategory.DISCOUNT
        }
    }
}
