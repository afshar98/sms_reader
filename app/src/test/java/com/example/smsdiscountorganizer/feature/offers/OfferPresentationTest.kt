package com.example.smsdiscountorganizer.feature.offers

import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.Offer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OfferPresentationTest {
    private val zone = ZoneId.of("Asia/Tehran")

    @Test
    fun classifiesOffersByExpirationIncludingTodayAndUnknown() {
        val startOfToday = Instant.parse("2025-04-01T00:00:00Z").toEpochMilli()
        val offers = listOf(
            offer(1, startOfToday - 1),
            offer(2, startOfToday + 1),
            offer(3, startOfToday + 86_400_000),
            offer(4, null),
        )

        assertEquals(OfferStatus.EXPIRED, offers[0].statusAt(startOfToday, zone))
        assertEquals(OfferStatus.EXPIRING_TODAY, offers[1].statusAt(startOfToday, zone))
        assertEquals(OfferStatus.ACTIVE, offers[2].statusAt(startOfToday, zone))
        assertEquals(OfferStatus.UNKNOWN, offers[3].statusAt(startOfToday, zone))
    }

    @Test
    fun filtersByQueryStatusAndMerchantThenSortsExpirationWithUnknownLast() {
        val today = Instant.parse("2025-04-01T12:00:00Z").toEpochMilli()
        val offers = listOf(
            offer(1, today + 60_000, merchant = "فروشگاه آبی", title = "پوشاک", code = "AbC10"),
            offer(2, today + 30_000, merchant = "فروشگاه قرمز", title = "کفش"),
            offer(3, null, merchant = "فروشگاه آبی", title = "کیف"),
        )

        val result = filterOffers(
            offers = offers,
            query = "abc",
            status = OfferStatusFilter.ALL,
            merchant = "فروشگاه آبی",
            nowMillis = today,
            zoneId = zone,
        )

        assertEquals(listOf(1L), result.map(Offer::sourceSmsId))
        val allByExpiry = filterOffers(offers, "", OfferStatusFilter.ALL, null, today, zone)
        assertEquals(listOf(2L, 1L, 3L), allByExpiry.map(Offer::sourceSmsId))
    }

    private fun offer(
        id: Long,
        expiry: Long?,
        merchant: String = "فروشگاه",
        title: String = "پیشنهاد",
        code: String? = null,
    ) = Offer(
        sourceSmsId = id,
        title = title,
        merchantName = merchant,
        discountCode = code,
        discountValue = "۱۰٪",
        percentageDiscounts = listOf(10.0),
        fixedDiscounts = listOf(MonetaryAmount("100000", MoneyCurrency.Toman)),
        freeShipping = null,
        minimumPurchase = null,
        productCategory = null,
        extractionConfidence = 0.9,
        validUntilMillis = expiry,
        sourceDateMillis = 0L,
        extractedAtMillis = 0L,
    )
}
