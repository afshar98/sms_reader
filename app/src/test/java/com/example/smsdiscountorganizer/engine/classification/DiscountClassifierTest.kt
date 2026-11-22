package com.example.smsdiscountorganizer.engine.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DiscountClassifierTest(
    private val case: SyntheticClassificationCase,
) {
    @Test
    fun classifiesSyntheticMessage() {
        val result = DiscountClassifier().classify(case.message)

        assertEquals(case.name, case.expected, result.category)
        assertTrue(case.name, result.confidence in 0.0..1.0)
        if (case.expected != ClassificationCategory.UNKNOWN) {
            assertTrue(case.name, result.reasons.isNotEmpty())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): Collection<Array<Any>> =
            SyntheticClassificationFixture.cases.map { arrayOf(it) }
    }
}

class DiscountClassifierPrecisionTest {
    @Test
    fun fixturePrecisionIsAtLeastNinetyFivePercent() {
        assertTrue(
            "fixtureCases=${SyntheticClassificationFixture.cases.size}",
            SyntheticClassificationFixture.cases.size >= 60,
        )
        val classifier = DiscountClassifier()
        val classifiedDiscounts = SyntheticClassificationFixture.cases.map { case ->
            case to classifier.classify(case.message)
        }.filter { (_, result) -> result.category == ClassificationCategory.DISCOUNT }
        val truePositives = classifiedDiscounts.count { (case, _) ->
            case.expected == ClassificationCategory.DISCOUNT
        }
        val falsePositives = classifiedDiscounts.size - truePositives
        assertTrue("predictedDiscounts=${classifiedDiscounts.size}", classifiedDiscounts.isNotEmpty())
        val precision = truePositives.toDouble() / classifiedDiscounts.size

        assertTrue(
            "precision=$precision, truePositives=$truePositives, falsePositives=$falsePositives",
            precision >= 0.95,
        )
    }
}

class DiscountClassifierBehaviorTest {
    private val classifier = DiscountClassifier()

    @Test
    fun multipleIndependentDiscountRulesIncreaseConfidence() {
        val singleSignal = classifier.classify("تخفیف")
        val multipleSignals = classifier.classify("کد تخفیف و حراج امروز")

        assertTrue(multipleSignals.confidence > singleSignal.confidence)
    }

    @Test
    fun unknownMessagesHaveNoRuleReasons() {
        val result = classifier.classify("سلام، فردا ساعت ۹ می‌بینمت")

        assertEquals(ClassificationCategory.UNKNOWN, result.category)
        assertEquals(0.0, result.confidence, 0.0)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun debugReasonsContainRuleDescriptionsButNeverTheInputBody() {
        val message = "پیشنهاد ویژه secret-code-7482 برای شما"

        val result = classifier.classify(message)

        assertEquals(ClassificationCategory.DISCOUNT, result.category)
        assertTrue(result.reasons.isNotEmpty())
        assertTrue(result.reasons.none { it.explanation.contains(message) })
        assertTrue(result.reasons.none { it.explanation.contains("secret-code-7482") })
    }
}

data class SyntheticClassificationCase(
    val name: String,
    val message: String,
    val expected: ClassificationCategory,
) {
    override fun toString(): String = name
}

object SyntheticClassificationFixture {
    private fun discount(name: String, message: String) =
        SyntheticClassificationCase(name, message, ClassificationCategory.DISCOUNT)
    private fun promotion(name: String, message: String) =
        SyntheticClassificationCase(name, message, ClassificationCategory.PROMOTIONAL_NON_DISCOUNT)
    private fun transaction(name: String, message: String) =
        SyntheticClassificationCase(name, message, ClassificationCategory.TRANSACTIONAL)
    private fun otp(name: String, message: String) =
        SyntheticClassificationCase(name, message, ClassificationCategory.OTP)
    private fun unknown(name: String, message: String) =
        SyntheticClassificationCase(name, message, ClassificationCategory.UNKNOWN)

    val cases = listOf(
        discount("discount percentage", "فروشگاه ما ۲۰٪ تخفیف برای خریدهای امروز دارد"),
        discount("discount code", "کد تخفیف SALE20 تا آخر شب فعال است"),
        discount("discount code instruction", "با کد تخفیف هدیه سفارش بدهید"),
        discount("discount percentage phrase", "درصد تخفیف شما ۳۰ درصد است"),
        discount("discount percentage range", "درصد تخفیف محصولات تا ۴۰٪"),
        discount("discount percentage details", "بررسی درصد تخفیف در صفحه محصول"),
        discount("purchase with discount", "خرید با تخفیف برای اعضا"),
        discount("purchase discount period", "خرید با تخفیف تا پایان هفته"),
        discount("purchase discount invitation", "خرید با تخفیف ویژه ثبت کنید"),
        discount("free shipping", "ارسال رایگان برای همه سفارش‌ها"),
        discount("free shipping today", "ارسال رایگان در خرید امروز"),
        discount("free shipping order", "برای سفارش شما ارسال رایگان داریم"),
        discount("sales festival", "جشنواره فروش بهاره با تخفیف"),
        discount("sales festival period", "جشنواره فروش تا جمعه ادامه دارد"),
        discount("sales festival announcement", "جشنواره فروش ویژه آغاز شد"),
        discount("special offer with code", "پیشنهاد ویژه با کد تخفیف"),
        discount("special offer percent", "پیشنهاد ویژه امروز: ۱۵٪ تخفیف"),
        discount("special offer purchase", "پیشنهاد ویژه برای خرید با تخفیف"),
        discount("clearance sale", "حراج بزرگ پایان فصل"),
        discount("clearance percentage", "حراج همه کالاها تا ۵۰٪ تخفیف"),
        discount("clearance with code", "حراج زمستانی، کد تخفیف هم فعال است"),
        discount("discount percentage adjective", "تخفیف ۱۰ درصدی تا امشب"),
        discount("second purchase discount", "برای خرید دوم تخفیف بگیرید"),
        discount("loyalty discount", "تخفیف بیشتر برای مشتریان وفادار"),

        promotion("new products", "محصولات جدید در فروشگاه ما آماده خرید هستند"),
        promotion("new collection", "از مجموعه تابستانی ما دیدن کنید"),
        promotion("visit website", "برای دیدن محصولات تازه به سایت ما سر بزنید"),
        promotion("order invitation", "همین حالا سفارش خود را ثبت کنید"),
        promotion("special product sale", "فروش ویژه محصولات جدید، به ما سر بزنید"),
        promotion("new services", "با خدمات جدید ما تجربه‌ای بهتر داشته باشید"),
        promotion("join customers", "با ما همراه شوید و محصولات تازه را ببینید"),
        promotion("product exhibition", "نمایشگاه محصولات ما از فردا آغاز می‌شود"),
        promotion("online store", "از فروشگاه اینترنتی ما خرید کنید"),
        promotion("product browsing", "از محصولات جدید ما بازدید کنید"),
        promotion("new assortment", "این هفته محصولات متنوعی عرضه شده‌اند"),
        promotion("store hours", "فروشگاه ما تا ساعت ۱۰ باز است"),
        promotion("page visit", "به صفحه تازه ما مراجعه کنید"),
        promotion("customer community", "به جمع مشتریان فروشگاه ما بپیوندید"),
        promotion("product inquiry", "برای اطلاع از کالاهای تازه پیام دهید"),

        transaction("account withdrawal", "برداشت از حساب شما به مبلغ ۲۵۰۰۰۰ ریال انجام شد"),
        transaction("account deposit", "واریز وجه به حساب شما با موفقیت انجام شد"),
        transaction("fund transfer", "انتقال وجه انجام شد؛ شماره پیگیری ۱۲۳۴۵۶"),
        transaction("card transfer", "کارت به کارت با موفقیت انجام شد"),
        transaction("successful payment", "پرداخت موفق بود؛ رسید پرداخت صادر شد"),
        transaction("failed payment", "پرداخت ناموفق بود، دوباره تلاش کنید"),
        transaction("invoice", "فاکتور خرید شما صادر شد"),
        transaction("order confirmation", "سفارش شما ثبت شد و در حال آماده‌سازی است"),
        transaction("bank balance", "موجودی حساب شما ۸۵۰۰۰۰ ریال است"),
        transaction("card purchase", "خرید از کارت بانکی شما انجام شد"),
        transaction("utility payment", "قبض برق شما پرداخت شد"),
        transaction("wallet charge", "شارژ کیف پول با موفقیت انجام شد"),
        transaction("payment receipt", "رسید پرداخت در حساب کاربری شما قرار گرفت"),
        transaction("bank transaction", "تراکنش بانکی انجام شد؛ موجودی حساب به‌روز شد"),
        transaction("purchase receipt", "مبلغ خرید از کارت شما کسر شد"),
        transaction("bank activity suppresses discount wording", "واریز وجه انجام شد و عبارت تخفیف فقط در شرح تراکنش است"),

        otp("one time password", "رمز یکبار مصرف شما ۱۲۳۴۵۶ است"),
        otp("dynamic password", "رمز پویا برای پرداخت: ۶۵۴۳۲۱"),
        otp("login verification", "کد تایید ورود شما ۹۸۷۶ است"),
        otp("verified login", "کد تأیید حساب کاربری: ۱۲۳۴"),
        otp("English OTP", "OTP: 483920"),
        otp("security code", "کد امنیتی شما ۷۷۱۱ است"),
        otp("password request", "رمز عبور موقت برای ورود ارسال شد"),
        otp("sign in code", "برای ورود، کد ورود ۹۰۱۲ را وارد کنید"),
        otp("transaction authorization", "رمز پویا جهت تأیید تراکنش ۱۲۳۴ است"),
        otp("OTP suppresses discount wording", "رمز پویا ۱۲۳۴ است؛ کد تخفیف در پیام دیگری آمده بود"),
        otp("identity verification", "کد احراز هویت شما ۵۶۷۸ است"),
        otp("confirmation code", "کد تایید شماره همراه ۴۴۳۳"),
        otp("bank OTP", "رمز یک بار مصرف بانک: ۱۲۳۴۵"),
        otp("temporary password", "گذرواژه موقت شما برای ورود ۹۸۷۶ است"),
        otp("confirm request", "جهت تایید درخواست، کد ۷۶۵۴ را وارد نمایید"),
        otp("authentication", "کد اعتبارسنجی شما ۲۳۴۵ است"),

        unknown("weather", "امروز در تهران هوا آفتابی خواهد بود"),
        unknown("greeting", "سلام، روز خوبی داشته باشید"),
        unknown("meeting reminder", "جلسه فردا ساعت ۹ برگزار می‌شود"),
        unknown("bus schedule", "اتوبوس ساعت ۱۸:۳۰ به ایستگاه می‌رسد"),
        unknown("school notice", "کلاس امروز به دلیل بارندگی تعطیل است"),
        unknown("plain number", "شماره پیگیری پرونده شما ۴۵۶۷۸۹ است"),
        unknown("family message", "امشب برای شام به خانه ما بیا"),
        unknown("news notice", "خبرهای امروز را از بخش اخبار بخوانید"),
        unknown("appointment", "نوبت شما برای روز دوشنبه ثبت شده است"),
        unknown("address", "نشانی جدید را برای من ارسال کن"),
        unknown("generic statement", "این پیام جهت اطلاع شما ارسال شده است"),
        unknown("travel reminder", "پرواز شما فردا ساعت ۷ صبح است"),
        unknown("parcel transit", "بسته شما به مرکز توزیع رسید"),
        unknown("service status", "سامانه از ساعت ۲۳ تا ۲۴ به‌روزرسانی می‌شود"),
        unknown("plain question", "فردا چه ساعتی به خانه می‌رسی؟"),

        promotion("no discount", "این کالا تخفیف ندارد"),
        promotion("without discount", "برای این خرید بدون تخفیف حساب می‌شود"),
        promotion("expired coupon", "کد تخفیف منقضی شده و قابل استفاده نیست"),
        promotion("discount not applied", "تخفیف اعمال نمی‌شود"),
        promotion("excluded product", "تخفیف شامل این کالا نمی‌شود"),
        promotion("no discount offered", "در این محصول هیچ تخفیفی ارائه نمی‌شود"),
        promotion("coupon void", "کد تخفیف باطل شد"),
        promotion("discount cancelled", "تخفیف لغو شده است"),
        promotion("no discounts available", "ما فعلا تخفیفی نداریم"),
        promotion("sale without discount", "بدون تخفیف به فروش می‌رسد"),
        promotion("discount failed", "تخفیف این سفارش اعمال نشد"),
        promotion("coupon disabled", "استفاده از کد تخفیف مجاز نیست"),
        promotion("discount unavailable", "این کالا شامل پیشنهاد تخفیف نیست"),
        promotion("discount period ended", "تخفیف در این بازه زمانی نداریم"),
        promotion("free shipping unavailable", "فعلا ارسال رایگان نداریم"),
    )
}
