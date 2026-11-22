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
                title = "Example offer",
                merchantName = "Example merchant",
                discountCode = "DEMO10",
                discountValue = "10%",
                validUntilMillis = null,
            ),
            extractor.extract(message),
        )
    }

    @Test
    fun ordinarySmsDoesNotProduceAnOffer() {
        assertNull(extractor.extract(SmsMessage(16L, null, "private ordinary text", 100L)))
    }
}
