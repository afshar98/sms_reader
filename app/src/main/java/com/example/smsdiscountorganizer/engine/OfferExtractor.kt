package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import javax.inject.Inject

fun interface OfferExtractor {
    fun extract(message: SmsMessage): OfferMetadata?

    /** Existing extractors may ignore history; production extraction can use derived sender evidence. */
    fun extract(message: SmsMessage, senderProfile: SenderMerchantProfile?): OfferMetadata? = extract(message)
}

/** Temporary fixture-only extractor; it never copies message text into persisted metadata. */
class FakeOfferExtractor @Inject constructor() : OfferExtractor {
    override fun extract(message: SmsMessage): OfferMetadata? {
        if (message.body?.contains("[FAKE_OFFER]") != true) return null
        return OfferMetadata(
            merchantName = "Example merchant",
            offerTitle = "Example offer",
            percentageDiscounts = listOf(10.0),
            fixedDiscounts = null,
            freeShipping = null,
            couponCode = "DEMO10",
            minimumPurchase = null,
            productCategory = null,
            confidence = 0.99,
        )
    }
}
