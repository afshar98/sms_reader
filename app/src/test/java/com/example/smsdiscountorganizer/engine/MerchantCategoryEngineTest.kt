package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.offer.OfferCategory
import com.example.smsdiscountorganizer.engine.extraction.MerchantExtractor
import com.example.smsdiscountorganizer.engine.extraction.MerchantEvidence
import com.example.smsdiscountorganizer.engine.extraction.ProductCategoryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRegistryBehaviorTest {
    private val extractor = MerchantExtractor()

    @Test
    fun recognizesPersianAndEnglishAliasesAsOneMerchant() {
        assertEquals("دیجی‌کالا", extractor.resolve("تخفیف Digikala برای خرید شما")?.merchantName)
        assertEquals("دیجی‌کالا", extractor.resolve("کد تخفیف دیجی کالا")?.merchantName)
    }

    @Test
    fun knownSenderMappingIsUsedButGenericGatewayIsNotAMerchant() {
        val mapped = extractor.resolve("پیشنهاد ویژه", senderAddress = "SNAPPFOOD")
        assertEquals("اسنپ‌فود", mapped?.merchantName)
        assertEquals(MerchantEvidence.SENDER_ID, mapped?.evidence)

        assertNull(extractor.resolve("پیشنهاد ویژه فروشگاهی", senderAddress = "SMS"))
    }

    @Test
    fun explicitAndManualEvidenceAreRequiredForUnregisteredMerchantNames() {
        assertEquals("بازار مهر", extractor.resolve("نام فروشگاه: بازار مهر؛ ۲۰٪ تخفیف")?.merchantName)
        assertEquals("بازار مهر", extractor.resolve("۲۰٪ تخفیف", manualCorrection = "بازار مهر")?.merchantName)
        assertEquals(
            MerchantEvidence.MANUAL_CORRECTION,
            extractor.resolve("۲۰٪ تخفیف", senderAddress = "TOROB", manualCorrection = "بازار مهر")?.evidence,
        )
        assertNull(extractor.resolve("۲۰٪ تخفیف"))
    }

    @Test
    fun conflictingBodyBrandsAreNotGuessed() {
        assertNull(extractor.resolve("تخفیف دیجی‌کالا و ترب"))
    }
}

class OfferCategoryTaxonomyBehaviorTest {
    private val extractor = ProductCategoryExtractor()

    @Test
    fun recognizesMultipleSiblingCategoriesAndSuppressesGeneralFallback() {
        assertEquals(
            listOf(OfferCategory.Apparel, OfferCategory.Beauty),
            extractor.extractAll("تخفیف کفش و عطر برای خریدهای شما"),
        )
        assertEquals(
            listOf(OfferCategory.Apparel),
            extractor.extractAll("تخفیف همه محصولات، مخصوص کفش"),
        )
    }

    @Test
    fun distinguishesExplicitGeneralOffersFromUnknownCategory() {
        assertEquals(listOf(OfferCategory.GENERAL), extractor.extractAll("۲۰٪ تخفیف برای همه محصولات"))
        assertEquals(listOf(OfferCategory.UNKNOWN), extractor.extractAll("۲۰٪ تخفیف ویژه شما"))
        assertTrue(OfferCategory.Apparel.parentStableId != null)
    }

    @Test
    fun recognizesZeroWidthNonJoinerInMultiwordCategoryTerms() {
        assertEquals(listOf(OfferCategory.Food), extractor.extractAll("تخفیف فست‌فود"))
    }

    @Test
    fun keepsExplicitParentWhenNoLeafMatchesAndDropsItWhenLeafMatches() {
        assertEquals(
            listOf(OfferCategory.FOOD_AND_DRINK),
            extractor.extractAll("تخفیف خوراک و نوشیدنی"),
        )
        assertEquals(
            listOf(OfferCategory.Grocery),
            extractor.extractAll("تخفیف خوراک و نوشیدنی برای خواربار"),
        )
    }
}
