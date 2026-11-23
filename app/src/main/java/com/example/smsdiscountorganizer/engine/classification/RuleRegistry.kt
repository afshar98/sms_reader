package com.example.smsdiscountorganizer.engine.classification

class RuleRegistry(rules: List<ClassificationRule> = defaultClassificationRules()) {
    val rules: List<ClassificationRule> = rules.toList()

    init {
        require(this.rules.map(ClassificationRule::id).distinct().size == this.rules.size) {
            "Classification rule IDs must be unique"
        }
        require(this.rules.all { it.id.isNotBlank() && it.weight > 0 && it.priority >= 0 }) {
            "Classification rules need an ID, positive weight, and non-negative priority"
        }
    }

    fun matchingRules(normalizedText: String): List<ClassificationRule> =
        rules.filter { it.matches(normalizedText) }

    companion object {
        private const val OVERRIDE_OTP = 100
        private const val OVERRIDE_TRANSACTION = 90
        private const val OVERRIDE_NEGATION = 80

        fun defaultClassificationRules(): List<ClassificationRule> = listOf(
            keywordRule(
                id = "otp_explicit_phrase",
                category = ClassificationCategory.OTP,
                weight = 1_000,
                priority = OVERRIDE_OTP,
                explanation = "Explicit one-time password or verification wording was detected.",
                phrases = listOf(
                    "رمز یکبار مصرف",
                    "رمز یک بار مصرف",
                    "رمز پویا",
                    "کد تایید",
                    "کد تأیید",
                    "کد امنیتی",
                    "کد ورود",
                    "رمز ورود",
                    "رمز عبور",
                    "کد احراز هویت",
                    "کد اعتبارسنجی",
                    "کد فعالسازی",
                    "کد فعال سازی",
                    "گذرواژه موقت",
                    "جهت تایید",
                    "جهت تأیید",
                    "otp",
                ),
            ),
            keywordRule(
                id = "transaction_bank_activity",
                category = ClassificationCategory.TRANSACTIONAL,
                weight = 900,
                priority = OVERRIDE_TRANSACTION,
                explanation = "Bank balance, transfer, or account activity wording was detected.",
                phrases = listOf(
                    "تراکنش",
                    "برداشت از حساب",
                    "برداشت وجه",
                    "واریز وجه",
                    "واریز به حساب",
                    "انتقال وجه",
                    "انتقال کارت",
                    "کارت به کارت",
                    "موجودی حساب",
                    "موجودی کارت",
                    "مانده حساب",
                    "کارت بانکی",
                    "از کارت شما کسر",
                    "از حساب شما کسر",
                ),
            ),
            keywordRule(
                id = "transaction_payment_notification",
                category = ClassificationCategory.TRANSACTIONAL,
                weight = 900,
                priority = OVERRIDE_TRANSACTION,
                explanation = "A payment, invoice, or completed order notification was detected.",
                phrases = listOf(
                    "پرداخت موفق",
                    "پرداخت ناموفق",
                    "پرداخت انجام شد",
                    "پرداخت شما",
                    "رسید پرداخت",
                    "فاکتور خرید",
                    "فاکتور شما",
                    "سفارش شما ثبت شد",
                    "سفارش شما آماده",
                    "قبض شما پرداخت شد",
                    "شارژ کیف پول",
                    "خرید از کارت",
                    "مبلغ خرید از کارت",
                ),
            ),
            regexRule(
                id = "transaction_utility_payment",
                category = ClassificationCategory.TRANSACTIONAL,
                weight = 900,
                priority = OVERRIDE_TRANSACTION,
                explanation = "A utility bill or invoice payment was detected.",
                pattern = Regex("قبض.{0,30}(?:پرداخت|مبلغ|مهلت)"),
            ),
            regexRule(
                id = "discount_explicit_negation",
                category = ClassificationCategory.PROMOTIONAL_NON_DISCOUNT,
                weight = 850,
                priority = OVERRIDE_NEGATION,
                explanation = "The message explicitly says a discount or free-shipping benefit is unavailable.",
                pattern = Regex(
                    "(?:بدون|فاقد|عدم)\\s*(?:هرگونه\\s*)?تخفیف|" +
                        "تخفیف(?:ی)?\\s*(?:ندارد|نداریم|موجود\\s*نیست|لغو\\s*شد|لغو\\s*شده|" +
                        "اعمال\\s*نشد|اعمال\\s*نمی[‌\\s]*شود|شامل\\s*نمی[‌\\s]*شود|" +
                        "ارائه\\s*نمی[‌\\s]*شود|نمی[‌\\s]*شود|نمی[‌\\s]*کنیم)|" +
                        "(?:تخفیف|کد\\s*تخفیف).{0,30}(?:نیست|ندارد|نداریم|اعمال\\s*نشد|" +
                        "اعمال\\s*نمی[‌\\s]*شود|شامل\\s*نمی[‌\\s]*شود|مجاز\\s*نیست|" +
                        "ارائه\\s*نمی[‌\\s]*شود|نمی[‌\\s]*شود)|" +
                        "کد\\s*تخفیف.{0,30}(?:باطل|منقضی|غیرفعال|نامعتبر|لغو)|" +
                        "ارسال\\s*رایگان\\s*(?:نداریم|نیست|نمی[‌\\s]*شود)",
                ),
            ),
            keywordRule(
                id = "discount_keyword",
                category = ClassificationCategory.DISCOUNT,
                weight = 55,
                explanation = "Explicit discount wording was detected.",
                phrases = listOf("تخفیف"),
            ),
            keywordRule(
                id = "discount_code",
                category = ClassificationCategory.DISCOUNT,
                weight = 70,
                explanation = "A discount code offer was detected.",
                phrases = listOf("کد تخفیف"),
            ),
            keywordRule(
                id = "discount_percentage",
                category = ClassificationCategory.DISCOUNT,
                weight = 75,
                explanation = "A percentage discount offer was detected.",
                phrases = listOf("درصد تخفیف"),
            ),
            keywordRule(
                id = "discounted_purchase",
                category = ClassificationCategory.DISCOUNT,
                weight = 70,
                explanation = "A purchase explicitly offered with a discount was detected.",
                phrases = listOf("خرید با تخفیف"),
            ),
            keywordRule(
                id = "free_shipping_offer",
                category = ClassificationCategory.DISCOUNT,
                weight = 70,
                explanation = "Free shipping was offered as a purchase benefit.",
                phrases = listOf("ارسال رایگان"),
            ),
            keywordRule(
                id = "sales_festival",
                category = ClassificationCategory.DISCOUNT,
                weight = 65,
                explanation = "A sales festival was detected.",
                phrases = listOf("جشنواره فروش"),
            ),
            keywordRule(
                id = "special_offer",
                category = ClassificationCategory.DISCOUNT,
                weight = 65,
                explanation = "A special offer was detected.",
                phrases = listOf("پیشنهاد ویژه"),
            ),
            keywordRule(
                id = "clearance_sale",
                category = ClassificationCategory.DISCOUNT,
                weight = 65,
                explanation = "A clearance sale was detected.",
                phrases = listOf("حراج"),
            ),
            keywordRule(
                id = "discount_campaign",
                category = ClassificationCategory.DISCOUNT,
                weight = 65,
                explanation = "A named shopping campaign associated with special pricing was detected.",
                phrases = listOf(
                    "جمعه سیاه",
                    "بلک فرایدی",
                ),
            ),
            keywordRule(
                id = "generic_promotion",
                category = ClassificationCategory.PROMOTIONAL_NON_DISCOUNT,
                weight = 40,
                explanation = "General advertising or product-promotion wording was detected without a discount signal.",
                phrases = listOf(
                    "محصولات جدید",
                    "محصول جدید",
                    "محصولات تازه",
                    "کالاهای تازه",
                    "مجموعه تابستانی",
                    "فروش ویژه",
                    "قسطی جمعه",
                    "قسطی‌جمعه",
                    "شگفت انگیز",
                    "شگفت‌انگیز",
                    "فروشگاه اینترنتی",
                    "خرید کنید",
                    "دیدن کنید",
                    "سفارش خود را ثبت کنید",
                    "از فروشگاه ما دیدن کنید",
                    "به سایت ما سر بزنید",
                    "به ما بپیوندید",
                    "با ما همراه شوید",
                    "خدمات جدید",
                    "نمایشگاه محصولات",
                    "فروشگاه ما",
                    "محصولات متنوعی عرضه",
                    "به صفحه تازه ما مراجعه کنید",
                    "برای اطلاع از کالاهای تازه",
                ),
            ),
        )

        private fun keywordRule(
            id: String,
            category: ClassificationCategory,
            weight: Int,
            priority: Int = 0,
            explanation: String,
            phrases: List<String>,
        ): ClassificationRule = object : ClassificationRule {
            override val id = id
            override val category = category
            override val weight = weight
            override val priority = priority
            override val explanation = explanation

            override fun matches(normalizedText: String): Boolean = phrases.any(normalizedText::contains)
        }

        private fun regexRule(
            id: String,
            category: ClassificationCategory,
            weight: Int,
            priority: Int,
            explanation: String,
            pattern: Regex,
        ): ClassificationRule = object : ClassificationRule {
            override val id = id
            override val category = category
            override val weight = weight
            override val priority = priority
            override val explanation = explanation

            override fun matches(normalizedText: String): Boolean = pattern.containsMatchIn(normalizedText)
        }
    }
}
