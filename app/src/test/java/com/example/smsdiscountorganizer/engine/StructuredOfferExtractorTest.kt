package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.domain.offer.OfferMetadata
import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.extraction.CouponCodeExtractor
import com.example.smsdiscountorganizer.engine.extraction.DiscountValueExtractor
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import com.example.smsdiscountorganizer.engine.extraction.ProductCategoryExtractor
import com.example.smsdiscountorganizer.engine.extraction.PurchaseConditionExtractor
import com.example.smsdiscountorganizer.engine.expiration.PersianDateResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StructuredOfferExtractorFixtureTest(
    private val fixture: OfferExtractionFixture,
) {
    @Test
    fun extractsOnlyMessagesWithDiscountSignals() {
        val result = PersianOfferExtractor().extract(message(fixture.message))

        assertEquals(fixture.name, fixture.shouldExtract, result != null)
        if (fixture.shouldExtract) assertTrue(fixture.name, result!!.confidence > 0.0)
    }

    private fun message(body: String) = SmsMessage(id = 1L, address = null, body = body, dateMillis = 10L)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): Collection<Array<Any>> = OfferExtractionFixture.cases.map { arrayOf(it) }
    }
}

class StructuredOfferExtractorDetailTest {
    private val extractor = PersianOfferExtractor()

    @Test
    fun extractsPercentageAndFixedDiscountExpressionsSeparately() {
        val result = extract("۱۰٪ تخفیف و ۵۰۰۰ تومان تخفیف")

        assertEquals(listOf(10.0), result.percentageDiscounts)
        assertEquals(listOf(MonetaryAmount("5000", MoneyCurrency.Toman)), result.fixedDiscounts)
    }

    @Test
    fun minimumPurchaseIsNotReportedAsDiscountAmount() {
        val result = extract("خرید بالای ۱۰۰ هزار تومان، ۲۰٪ تخفیف")

        assertEquals(listOf(20.0), result.percentageDiscounts)
        assertNull(result.fixedDiscounts)
        assertEquals(MonetaryAmount("100000", MoneyCurrency.Toman), result.minimumPurchase)
    }

    @Test
    fun supportsMultiplePercentagesAndFixedAmounts() {
        val result = extract("۱۰٪ تخفیف و ۵۰۰۰ تومان تخفیف؛ ۱۵ درصد تخفیف و ۲۰۰۰۰ ریال تخفیف")

        assertEquals(listOf(10.0, 15.0), result.percentageDiscounts)
        assertEquals(
            listOf(
                MonetaryAmount("5000", MoneyCurrency.Toman),
                MonetaryAmount("20000", MoneyCurrency.Rial),
            ),
            result.fixedDiscounts,
        )
    }

    @Test
    fun extractsFreeShippingWithoutInventingOtherFields() {
        val result = extract("ارسال رایگان برای سفارش امروز")

        assertEquals(true, result.freeShipping)
        assertNull(result.merchantName)
        assertNull(result.offerTitle)
        assertNull(result.percentageDiscounts)
        assertNull(result.fixedDiscounts)
        assertNull(result.couponCode)
        assertNull(result.minimumPurchase)
        assertNull(result.productCategory)
    }

    @Test
    fun preservesCouponCodeCaseAndPunctuation() {
        val result = extract("کد تخفیف: SpRiNg-20_X برای خرید پوشاک")

        assertEquals("SpRiNg-20_X", result.couponCode)
    }

    @Test
    fun extractsLowercaseCouponAfterStandaloneCodeLabelOnAnotherLine() {
        assertEquals("xyzp", CouponCodeExtractor().extract("کد تخفیف تپسی‌مارکت\nکد: xyzp"))
        assertEquals("AbC123", CouponCodeExtractor().extract("کد تخفیف شما: AbC123"))
    }

    @Test
    fun doesNotExtractOtpAsCouponCode() {
        assertNull(CouponCodeExtractor().extract("رمز پویا 839204 برای ورود؛ کد تخفیف بعدا ارسال می‌شود"))
        assertNull(extractor.extract(message("رمز یکبار مصرف 839204؛ برای خرید تخفیف هم دارید")))
    }

    @Test
    fun extractsExplicitMerchantTitleAndCategory() {
        val result = extract("نام فروشگاه: پوشاک باران؛ عنوان پیشنهاد: خرید بهاره؛ ۲۰٪ تخفیف")

        assertEquals("پوشاک باران", result.merchantName)
        assertEquals("خرید بهاره", result.offerTitle)
        assertEquals(ProductCategory.Apparel, result.productCategory)
    }

    @Test
    fun merchantIsNullWhenOnlyGenericStoreWordingExists() {
        val result = extract("فروشگاه ما ۲۰٪ تخفیف برای خرید دارد")

        assertNull(result.merchantName)
    }

    @Test
    fun directExtractorsKeepMinimumPurchaseAndDiscountAmountDistinct() {
        val text = "حداقل خرید ۱۰۰ هزار تومان، با ۵۰۰۰ تومان تخفیف"

        assertEquals(
            MonetaryAmount("100000", MoneyCurrency.Toman),
            PurchaseConditionExtractor().extractMinimumPurchase(text),
        )
        assertEquals(
            listOf(MonetaryAmount("5000", MoneyCurrency.Toman)),
            DiscountValueExtractor().extract(text).fixedDiscounts,
        )
    }

    @Test
    fun detailResultContainsNoRawSmsBody() {
        val original = "فروشگاه آریا: کد تخفیف AbC55؛ ۲۰٪ تخفیف برای خرید گوشی"
        val result = extract(original)

        assertEquals("AbC55", result.couponCode)
        assertNotNull(result.confidence)
        assertTrue(result.couponCode != original)
    }

    @Test
    fun preservesRepeatedDiscountExpressions() {
        val result = extract("۱۰٪ تخفیف اول؛ ۱۰٪ تخفیف دوم")

        assertEquals(listOf(10.0, 10.0), result.percentageDiscounts)
    }

    @Test
    fun extractsFoodAndGroceryCategoriesWithoutSubstringCollision() {
        assertEquals(ProductCategory.Grocery, extract("جشنواره فروش مواد غذایی").productCategory)
        assertEquals(ProductCategory.Food, extract("تخفیف غذای رستوران").productCategory)
    }

    @Test
    fun mapsProductTermsToMultipleSpecificCategoriesAndTapsiMarketToGrocery() {
        val extractor = ProductCategoryExtractor()

        assertEquals(
            listOf(com.example.smsdiscountorganizer.domain.offer.OfferCategory.Electronics, com.example.smsdiscountorganizer.domain.offer.OfferCategory.Jewelry),
            extractor.extractAll("هندزفری و پلاک طلا"),
        )
        assertEquals(ProductCategory.Grocery, extractor.extract("تپسی‌مارکت"))
    }

    @Test
    fun missingAndMalformedFieldsRemainNull() {
        val result = extract("پیشنهاد ویژه برای اعضا")

        assertNull(result.merchantName)
        assertNull(result.offerTitle)
        assertNull(result.percentageDiscounts)
        assertNull(result.fixedDiscounts)
        assertNull(result.freeShipping)
        assertNull(result.couponCode)
        assertNull(result.minimumPurchase)
        assertNull(result.productCategory)
    }

    @Test
    fun extractionUsesSmsReceivedTimestampForRelativeExpiration() {
        val zone = ZoneId.of("Asia/Tehran")
        val receivedAt = Instant.parse("2025-03-20T20:30:00Z")
        val dateResolver = PersianDateResolver(Clock.fixed(receivedAt, zone), zone)
        val offer = PersianOfferExtractor(expirationResolver = dateResolver)
            .extract(SmsMessage(3L, null, "۲۰٪ تخفیف تا پایان امروز", receivedAt.toEpochMilli()))!!

        assertEquals(
            Instant.parse("2025-03-21T20:29:59.999999999Z").toEpochMilli(),
            offer.validUntilMillis,
        )
    }

    private fun extract(body: String): OfferMetadata = extractor.extract(message(body))!!

    private fun message(body: String) = SmsMessage(id = 2L, address = null, body = body, dateMillis = 20L)
}

data class OfferExtractionFixture(
    val name: String,
    val message: String,
    val shouldExtract: Boolean,
) {
    override fun toString(): String = name

    companion object {
        val cases = listOf(
            positive("percent sign", "۲۰٪ تخفیف برای شما"),
            positive("word percent", "۲۰ درصد تخفیف برای شما"),
            positive("discount before percent", "تخفیف ۱۵٪ تا پایان امروز"),
            positive("discounted percentage adjective", "تخفیف ۱۵ درصدی برای خرید"),
            positive("percentage cap", "تا ۳۰٪ تخفیف در حراج"),
            positive("Persian digits", "۳۵٪ تخفیف امروز"),
            positive("Arabic digits", "٣٥٪ تخفیف امروز"),
            positive("decimal percentage", "۱۲٫۵٪ تخفیف برای اعضا"),
            positive("discount percentage label", "درصد تخفیف ۲۵ برای خرید پوشاک"),
            positive("two discounts", "۱۰٪ تخفیف و ۵۰۰۰ تومان تخفیف"),
            positive("fixed toman", "۵۰۰۰ تومان تخفیف برای خرید"),
            positive("fixed toman suffix", "تخفیف ۵۰۰۰ تومانی برای شما"),
            positive("fixed toman multiplier", "۱۰ هزار تومان تخفیف"),
            positive("fixed rial", "۲۰۰۰۰ ریال تخفیف برای مشتریان"),
            positive("multiple fixed values", "۵۰۰۰ تومان تخفیف و ۲۰۰۰۰ ریال تخفیف"),
            positive("free shipping", "ارسال رایگان برای سفارش‌ها"),
            positive("free delivery", "با خرید امروز ارسال رایگان دارید"),
            positive("sales festival", "جشنواره فروش آغاز شد"),
            positive("special offer", "پیشنهاد ویژه برای اعضای باشگاه"),
            positive("clearance", "حراج آخر فصل تا جمعه"),
            positive("purchase discount", "خرید با تخفیف فقط امروز"),
            positive("discount code", "کد تخفیف SAVE20 را وارد کنید"),
            positive("case sensitive code", "کد تخفیف SuMmEr25 فعال است"),
            positive("code with punctuation", "کد تخفیف AB-20_X برای خرید"),
            positive("minimum purchase", "خرید بالای ۱۰۰ هزار تومان و ۲۰٪ تخفیف"),
            positive("minimum order", "حداقل سفارش ۲۵۰۰۰ تومان؛ ارسال رایگان"),
            positive("minimum plus fixed discount", "حداقل خرید ۱۰۰ هزار تومان و ۵۰۰۰ تومان تخفیف"),
            positive("merchant label", "نام فروشگاه: آریا؛ ۲۰٪ تخفیف"),
            positive("explicit title", "عنوان پیشنهاد: خرید بهاره؛ ۱۵٪ تخفیف"),
            positive("apparel category", "۲۰٪ تخفیف برای خرید پوشاک"),
            positive("electronics category", "تخفیف خرید گوشی و لپتاپ"),
            positive("beauty category", "پیشنهاد ویژه محصولات آرایشی"),
            positive("food category", "ارسال رایگان غذای رستوران"),
            positive("home category", "حراج لوازم خانگی"),
            positive("travel category", "پیشنهاد ویژه رزرو هتل و سفر"),
            positive("grocery category", "جشنواره فروش مواد غذایی"),
            positive("entertainment category", "تخفیف بلیط سینما"),
            positive("discount no title", "۲۰٪ تخفیف برای خرید امروز"),
            positive("free shipping and code", "ارسال رایگان با کد تخفیف ShipNow"),
            positive("festival percentage", "جشنواره فروش با ۲۵ درصد تخفیف"),

            negative("one time password", "رمز یکبار مصرف شما ۱۲۳۴۵۶ است"),
            negative("dynamic password", "رمز پویا ۸۳۹۲۰۴ برای ورود"),
            negative("verification code", "کد تایید شما ۵۶۷۸ است"),
            negative("OTP and discount wording", "رمز پویا ۸۳۹۲۰۴؛ تخفیف در پیام دیگری بود"),
            negative("bank withdrawal", "برداشت از حساب؛ تخفیف فقط در شرح تراکنش"),
            negative("payment receipt", "رسید پرداخت صادر شد؛ عبارت تخفیف در توضیحات است"),
            negative("card transfer", "کارت به کارت انجام شد؛ پیام حاوی کد تخفیف نبود"),
            negative("bank balance", "موجودی حساب شما؛ تخفیف در متن تبلیغاتی نبود"),
            negative("purchase payment", "پرداخت موفق شد؛ بدون تخفیف در رسید"),
            negative("discount denied", "این کالا تخفیف ندارد"),
            negative("discount not applied", "تخفیف اعمال نمی‌شود"),
            negative("expired code", "کد تخفیف منقضی شده است"),
            negative("no free shipping", "ارسال رایگان نداریم"),
            negative("negative discount mention", "این پیشنهاد شامل تخفیف نمی‌شود"),
            negative("generic new products", "محصولات جدید ما را در سایت ببینید"),
            negative("generic sales message", "فروش ویژه محصولات تازه، همین حالا خرید کنید"),
            negative("store invitation", "به فروشگاه اینترنتی ما سر بزنید"),
            negative("new services", "با خدمات جدید ما همراه شوید"),
            negative("weather", "امروز هوا آفتابی است"),
            negative("delivery status", "بسته شما به مرکز توزیع رسید"),
        )

        private fun positive(name: String, message: String) = OfferExtractionFixture(name, message, true)
        private fun negative(name: String, message: String) = OfferExtractionFixture(name, message, false)
    }
}
