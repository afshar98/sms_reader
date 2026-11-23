package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.OfferExtractor
import com.example.smsdiscountorganizer.engine.classification.ClassificationCategory
import com.example.smsdiscountorganizer.engine.classification.DiscountClassifier
import com.example.smsdiscountorganizer.engine.expiration.ExpirationResolution
import com.example.smsdiscountorganizer.engine.expiration.PersianDateResolver
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.time.Clock
import java.time.ZoneId

class PersianOfferExtractor(
    private val classifier: DiscountClassifier = DiscountClassifier(),
    private val merchantExtractor: MerchantExtractor = MerchantExtractor(),
    private val discountValueExtractor: DiscountValueExtractor = DiscountValueExtractor(),
    private val couponCodeExtractor: CouponCodeExtractor = CouponCodeExtractor(),
    private val purchaseConditionExtractor: PurchaseConditionExtractor = PurchaseConditionExtractor(),
    private val productCategoryExtractor: ProductCategoryExtractor = ProductCategoryExtractor(),
    private val titleExtractor: OfferTitleExtractor = OfferTitleExtractor(),
    private val expirationResolver: PersianDateResolver = defaultExpirationResolver(),
    private val textNormalizer: PersianTextNormalizer = PersianTextNormalizer(),
) : OfferExtractor {
    override fun extract(message: SmsMessage): OfferMetadata? = extract(message, null)

    override fun extract(message: SmsMessage, senderProfile: SenderMerchantProfile?): OfferMetadata? {
        val originalBody = message.body ?: return null
        val body = textNormalizer.normalize(originalBody)
        val classification = classifier.classify(body)
        if (classification.category != ClassificationCategory.DISCOUNT) return null

        val merchantName = when (val current = merchantExtractor.resolveCurrent(body)) {
            is CurrentMerchantEvidence.Identified -> current.merchantName
            CurrentMerchantEvidence.Ambiguous -> null
            CurrentMerchantEvidence.None -> when {
                senderProfile != null -> senderProfile.resolvedMerchantName
                else -> merchantExtractor.resolve(body, senderAddress = message.address)?.merchantName
            }
        }

        val discountValues = discountValueExtractor.extract(body)
        val expirationText = EXPIRATION_CONTEXT.find(body)?.let { body.substring(it.range.first) } ?: body
        val expiration = expirationResolver.resolve(expirationText, message.dateMillis)
            as? ExpirationResolution.Resolved
        val freeShipping = when {
            FREE_SHIPPING_NEGATION.containsMatchIn(body) -> false
            FREE_SHIPPING.containsMatchIn(body) -> true
            else -> null
        }
        return OfferMetadata(
            merchantName = merchantName,
            offerTitle = titleExtractor.extract(body),
            percentageDiscounts = discountValues.percentageDiscounts,
            fixedDiscounts = discountValues.fixedDiscounts,
            freeShipping = freeShipping,
            couponCode = couponCodeExtractor.extract(originalBody),
            minimumPurchase = purchaseConditionExtractor.extractMinimumPurchase(body),
            productCategory = productCategoryExtractor.extract(body),
            confidence = classification.confidence,
            validUntilMillis = expiration?.expiresAt?.toEpochMilli(),
            applicableCategories = productCategoryExtractor.extractAll(body),
        )
    }

    private companion object {
        fun defaultExpirationResolver(): PersianDateResolver {
            val zoneId = ZoneId.systemDefault()
            return PersianDateResolver(Clock.system(zoneId), zoneId)
        }

        val FREE_SHIPPING = Regex("ارسال\\s*رایگان|پست\\s*رایگان")
        val FREE_SHIPPING_NEGATION = Regex("(?:ارسال|پست)\\s*رایگان\\s*(?:نیست|نداریم|نمی[‌\\s]*شود)")
        val EXPIRATION_CONTEXT = Regex("(?:تا\\s+|مهلت(?:\\s+استفاده)?\\s*[:：]?\\s*|انقضا(?:ی)?\\s*[:：]?\\s*|اعتبار\\s*[:：]?\\s*|expires?\\s+|valid\\s+until\\s+)", RegexOption.IGNORE_CASE)
    }
}
