package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.OfferCategory
import com.example.smsdiscountorganizer.engine.extraction.MerchantExtractor
import com.example.smsdiscountorganizer.engine.extraction.ProductCategoryExtractor
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantCategoryEvaluationTest {
    private val merchants = MerchantExtractor()
    private val categories = ProductCategoryExtractor()

    @Test
    fun evaluatesAtLeastTwoHundredSyntheticPersianOfferMessages() {
        val cases = SyntheticMerchantCategoryCases.all
        val merchantCorrect = cases.count { case ->
            merchants.resolve(case.message, case.senderAddress, case.manualCorrection)?.merchantName == case.expectedMerchant
        }
        val merchantAccuracy = merchantCorrect.toDouble() / cases.size

        var truePositive = 0
        var falsePositive = 0
        var falseNegative = 0
        val matrix = OfferCategory.entries.associateWith { MutableConfusion() }
        cases.forEach { case ->
            val actual = categories.extractAll(case.message).toSet()
            OfferCategory.entries.forEach { category ->
                val expectedPositive = category in case.expectedCategories
                val actualPositive = category in actual
                matrix.getValue(category).record(expectedPositive, actualPositive)
                when {
                    expectedPositive && actualPositive -> truePositive += 1
                    !expectedPositive && actualPositive -> falsePositive += 1
                    expectedPositive && !actualPositive -> falseNegative += 1
                }
            }
        }
        val precision = truePositive.toDouble() / (truePositive + falsePositive).coerceAtLeast(1)
        val recall = truePositive.toDouble() / (truePositive + falseNegative).coerceAtLeast(1)
        val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        val supportedF1 = matrix.mapNotNull { (_, values) ->
            val support = values.truePositive + values.falseNegative
            if (support == 0) null else {
                val categoryPrecision = values.truePositive.toDouble() /
                    (values.truePositive + values.falsePositive).coerceAtLeast(1)
                val categoryRecall = values.truePositive.toDouble() / support
                if (categoryPrecision + categoryRecall == 0.0) 0.0 else {
                    2 * categoryPrecision * categoryRecall / (categoryPrecision + categoryRecall)
                }
            }
        }
        val macroF1 = supportedF1.average()

        println("synthetic_cases=${cases.size}; merchant_exact_match=$merchantAccuracy ($merchantCorrect/${cases.size})")
        println("category_micro_precision=$precision; recall=$recall; f1=$f1; supported_macro_f1=$macroF1")
        println("category_confusion_matrix=category:TP,FP,FN,TN; " + matrix.entries.joinToString("; ") { (category, values) ->
            "${category.stableId}:${values.truePositive},${values.falsePositive},${values.falseNegative},${values.trueNegative}"
        })

        assertTrue("synthetic message count=${cases.size}", cases.size >= 200)
        assertTrue("merchant exact-match accuracy=$merchantAccuracy", merchantAccuracy >= 0.95)
        assertTrue("category micro F1=$f1", f1 >= 0.95)
    }
}

private data class MerchantCategoryCase(
    val message: String,
    val expectedMerchant: String?,
    val expectedCategories: Set<OfferCategory>,
    val senderAddress: String? = null,
    val manualCorrection: String? = null,
)

private data class MerchantScenario(
    val canonicalName: String,
    val aliases: List<String>,
)

private data class CategoryScenario(
    val category: OfferCategory,
    val cue: String,
)

private data class MutableConfusion(
    var truePositive: Int = 0,
    var falsePositive: Int = 0,
    var falseNegative: Int = 0,
    var trueNegative: Int = 0,
) {
    fun record(expected: Boolean, actual: Boolean) {
        when {
            expected && actual -> truePositive += 1
            !expected && actual -> falsePositive += 1
            expected && !actual -> falseNegative += 1
            else -> trueNegative += 1
        }
    }
}

private object SyntheticMerchantCategoryCases {
    private val merchantScenarios = listOf(
        MerchantScenario("دیجی‌کالا", listOf("دیجی کالا", "Digikala")),
        MerchantScenario("اسنپ‌فود", listOf("اسنپ‌فود", "SnappFood")),
        MerchantScenario("اسنپ‌مارکت", listOf("اسنپ مارکت", "SnappMarket")),
        MerchantScenario("تپسی", listOf("تپسی", "Tapsi")),
        MerchantScenario("ترب", listOf("ترب", "Torob")),
        MerchantScenario("بانی‌مد", listOf("بانی مد", "BaniMode")),
        MerchantScenario("خانومی", listOf("خانومی", "Khanoumi")),
        MerchantScenario("اُکالا", listOf("اکالا", "Okala")),
        MerchantScenario("دیجی‌استایل", listOf("دیجی استایل", "DigiStyle")),
        MerchantScenario("مدیسه", listOf("مدیسه", "Modiseh")),
    )

    private val categoryScenarios = listOf(
        CategoryScenario(OfferCategory.Apparel, "کفش"),
        CategoryScenario(OfferCategory.Grocery, "میوه"),
        CategoryScenario(OfferCategory.Electronics, "گوشی"),
        CategoryScenario(OfferCategory.Beauty, "عطر"),
        CategoryScenario(OfferCategory.Food, "رستوران"),
        CategoryScenario(OfferCategory.Home, "مبلمان"),
        CategoryScenario(OfferCategory.Travel, "هتل"),
        CategoryScenario(OfferCategory.Entertainment, "سینما"),
        CategoryScenario(OfferCategory.GENERAL, "همه محصولات"),
        CategoryScenario(OfferCategory.UNKNOWN, "این پیشنهاد برای شماست"),
    )

    val all: List<MerchantCategoryCase> = buildList {
        merchantScenarios.forEachIndexed { merchantIndex, merchant ->
            categoryScenarios.forEachIndexed { categoryIndex, category ->
                val alias = merchant.aliases[(merchantIndex + categoryIndex) % merchant.aliases.size]
                add(
                    MerchantCategoryCase(
                        message = "تخفیف $alias برای ${category.cue}",
                        expectedMerchant = merchant.canonicalName,
                        expectedCategories = setOf(category.category),
                    ),
                )
                add(
                    MerchantCategoryCase(
                        message = "کد تخفیف برای ${category.cue} در $alias",
                        expectedMerchant = merchant.canonicalName,
                        expectedCategories = setOf(category.category),
                    ),
                )
            }
        }
        add(MerchantCategoryCase("پیشنهاد ویژه", "اسنپ‌فود", setOf(OfferCategory.UNKNOWN), senderAddress = "SNAPPFOOD"))
        add(MerchantCategoryCase("تخفیف فروشگاهی", null, setOf(OfferCategory.UNKNOWN), senderAddress = "SMS"))
        add(MerchantCategoryCase("تخفیف عمومی", null, setOf(OfferCategory.UNKNOWN), senderAddress = "50004001"))
        add(MerchantCategoryCase("تخفیف بازار مهر", "بازار مهر", setOf(OfferCategory.UNKNOWN), manualCorrection = "بازار مهر"))
        add(MerchantCategoryCase("نام فروشگاه: بازار مهر؛ ۲۰٪ تخفیف", "بازار مهر", setOf(OfferCategory.UNKNOWN)))
        add(MerchantCategoryCase("نام فروشگاه: ما؛ ۲۰٪ تخفیف", null, setOf(OfferCategory.UNKNOWN)))
        add(MerchantCategoryCase("تخفیف کفش و عطر", null, setOf(OfferCategory.Apparel, OfferCategory.Beauty)))
        add(MerchantCategoryCase("تخفیف همه کالاها برای کفش", null, setOf(OfferCategory.Apparel)))
        add(MerchantCategoryCase("تخفیف Digikala و Torob برای کفش", null, setOf(OfferCategory.Apparel)))
        add(MerchantCategoryCase("تخفیف SnappFood برای کفش", "اسنپ‌فود", setOf(OfferCategory.Apparel), senderAddress = "TOROB"))
        add(MerchantCategoryCase("تخفیف تپسی‌پک", null, setOf(OfferCategory.UNKNOWN)))
        add(MerchantCategoryCase("تخفیف ویژه", "بازار مهر", setOf(OfferCategory.UNKNOWN), senderAddress = "TOROB", manualCorrection = "بازار مهر"))
        add(MerchantCategoryCase("۲۰٪ تخفیف", null, setOf(OfferCategory.UNKNOWN), senderAddress = "INFO"))
        add(MerchantCategoryCase("فروشگاه جدید ما، تخفیف ۲۰٪", null, setOf(OfferCategory.UNKNOWN)))
        add(MerchantCategoryCase("تخفیف همه محصولات و لوازم خانگی", null, setOf(OfferCategory.Home)))
        add(MerchantCategoryCase("تخفیف گوشی و لپتاپ", null, setOf(OfferCategory.Electronics)))
        add(MerchantCategoryCase("پیشنهاد برای خواربار و رستوران", null, setOf(OfferCategory.Food, OfferCategory.Grocery)))
        add(MerchantCategoryCase("پرواز ارزان با تخفیف", null, setOf(OfferCategory.Travel)))
        add(MerchantCategoryCase("پیشنهاد ویژه برای مراکز خرید", null, setOf(OfferCategory.SHOPPING)))
        add(MerchantCategoryCase("تخفیف خوراک و نوشیدنی", null, setOf(OfferCategory.FOOD_AND_DRINK)))
    }
}
