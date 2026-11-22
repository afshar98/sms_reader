package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeOfferExtractorTest {
    private val extractor = FakeOfferExtractor()

    @Test
    fun fixtureMarkerProducesFixedMetadataWithoutCopyingMessageBody() {
        val message = SmsMessage(15L, "+10000000000", "[FAKE_OFFER] private body", 100L)

        assertEquals(
            OfferMetadata(
                merchantName = "Example merchant",
                offerTitle = "Example offer",
                percentageDiscounts = listOf(10.0),
                fixedDiscounts = null,
                freeShipping = null,
                couponCode = "DEMO10",
                minimumPurchase = null,
                productCategory = null,
                confidence = 0.99,
            ),
            extractor.extract(message),
        )
    }

    @Test
    fun ordinarySmsDoesNotProduceAnOffer() {
        assertNull(extractor.extract(SmsMessage(16L, null, "private ordinary text", 100L)))
    }
}
