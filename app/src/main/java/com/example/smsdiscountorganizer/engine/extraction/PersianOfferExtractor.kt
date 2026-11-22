package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.OfferExtractor
import com.example.smsdiscountorganizer.engine.classification.ClassificationCategory
import com.example.smsdiscountorganizer.engine.classification.DiscountClassifier

class PersianOfferExtractor(
    private val classifier: DiscountClassifier = DiscountClassifier(),
    private val merchantExtractor: MerchantExtractor = MerchantExtractor(),
    private val discountValueExtractor: DiscountValueExtractor = DiscountValueExtractor(),
    private val couponCodeExtractor: CouponCodeExtractor = CouponCodeExtractor(),
    private val purchaseConditionExtractor: PurchaseConditionExtractor = PurchaseConditionExtractor(),
    private val productCategoryExtractor: ProductCategoryExtractor = ProductCategoryExtractor(),
    private val titleExtractor: OfferTitleExtractor = OfferTitleExtractor(),
) : OfferExtractor {
    override fun extract(message: SmsMessage): OfferMetadata? {
        val body = message.body ?: return null
        val classification = classifier.classify(body)
        if (classification.category != ClassificationCategory.DISCOUNT) return null

        val discountValues = discountValueExtractor.extract(body)
        val freeShipping = when {
            FREE_SHIPPING_NEGATION.containsMatchIn(body) -> false
            FREE_SHIPPING.containsMatchIn(body) -> true
            else -> null
        }
        return OfferMetadata(
            merchantName = merchantExtractor.extract(body),
            offerTitle = titleExtractor.extract(body),
            percentageDiscounts = discountValues.percentageDiscounts,
            fixedDiscounts = discountValues.fixedDiscounts,
            freeShipping = freeShipping,
            couponCode = couponCodeExtractor.extract(body),
            minimumPurchase = purchaseConditionExtractor.extractMinimumPurchase(body),
            productCategory = productCategoryExtractor.extract(body),
            confidence = classification.confidence,
        )
    }

    private companion object {
        val FREE_SHIPPING = Regex("ارسال\\s*رایگان|پست\\s*رایگان")
        val FREE_SHIPPING_NEGATION = Regex("(?:ارسال|پست)\\s*رایگان\\s*(?:نیست|نداریم|نمی[‌\\s]*شود)")
    }
}
