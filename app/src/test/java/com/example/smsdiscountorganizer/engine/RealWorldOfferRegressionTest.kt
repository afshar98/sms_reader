package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import com.example.smsdiscountorganizer.domain.offer.DiscountType
import com.example.smsdiscountorganizer.domain.offer.OfferCategory
import com.example.smsdiscountorganizer.domain.offer.discountType
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RealWorldOfferRegressionTest {
    private val extractor = PersianOfferExtractor()

    @Test
    fun extractsCouponOnlyTapsiMarketOfferThroughTonight() {
        val receivedAt = Instant.parse("2025-03-20T10:00:00Z").toEpochMilli()
        val result = extractor.extract(
            SmsMessage(
                id = 1L,
                address = null,
                body = "کد تخفیف تپسی‌مارکت تا امشب اعتبار داره!\nکد: xyzp",
                dateMillis = receivedAt,
            ),
        )

        assertNotNull("The explicit coupon-only offer must be extracted", result)
        result ?: return
        assertEquals("تپسی‌مارکت", result.merchantName)
        assertEquals("xyzp", result.couponCode)
        assertEquals(DiscountType.COUPON, result.discountType)
        assertNull(result.percentageDiscounts)
        assertNull(result.fixedDiscounts)
        assertEquals(
            Instant.parse("2025-03-20T20:29:59.999999999Z").toEpochMilli(),
            result.validUntilMillis,
        )
        assertEquals(ProductCategory.Grocery, result.productCategory)
        assertEquals(listOf(OfferCategory.Grocery), result.applicableCategories)
    }

    @Test
    fun extractsImplicitPersianPercentageAndKeepsNoExpiration() {
        val result = extractor.extract(
            SmsMessage(
                id = 2L,
                address = null,
                body = "هندزفری و پلاک طلا ۸۰٪\n۱۲ظهر، قسطی‌جمعه دیجی‌پی",
                dateMillis = Instant.parse("2025-03-20T10:00:00Z").toEpochMilli(),
            ),
        )

        assertNotNull("Product percentage plus campaign evidence must be extracted", result)
        result ?: return
        assertEquals(listOf(80.0), result.percentageDiscounts)
        assertEquals(DiscountType.PERCENTAGE, result.discountType)
        assertNull(result.merchantName)
        assertEquals("دیجی‌پی", result.campaignBrand)
        assertNull(result.productCategory)
        assertEquals(listOf(OfferCategory.Electronics, OfferCategory.Jewelry), result.applicableCategories)
        assertNull(result.fixedDiscounts)
        assertNull(result.couponCode)
        assertNull(result.validUntilMillis)
    }

    @Test
    fun unrelatedPercentagesDoNotBecomeDiscountOffers() {
        assertNull(extractor.extract(message("شارژ باتری گوشی ۸۰٪ است")))
    }

    @Test
    fun otpWithCodeLabelDoesNotBecomeCouponOffer() {
        assertNull(extractor.extract(message("رمز یکبار مصرف 839204؛ کد: AbC20 برای ورود")))
    }

    private fun message(body: String) = SmsMessage(3L, null, body, 1_742_462_400_000L)
}
