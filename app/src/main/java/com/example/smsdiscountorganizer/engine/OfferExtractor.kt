package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import javax.inject.Inject

fun interface OfferExtractor {
    fun extract(message: SmsMessage): OfferMetadata?
}

/** Temporary fixture-only extractor; it never copies message text into persisted metadata. */
class FakeOfferExtractor @Inject constructor() : OfferExtractor {
    override fun extract(message: SmsMessage): OfferMetadata? {
        if (message.body?.contains("[FAKE_OFFER]") != true) return null
        return OfferMetadata(
            title = "Example offer",
            merchantName = "Example merchant",
            discountCode = "DEMO10",
            discountValue = "10%",
            validUntilMillis = null,
        )
    }
}
