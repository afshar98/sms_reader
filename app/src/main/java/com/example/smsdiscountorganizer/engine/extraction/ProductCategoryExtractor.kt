package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import com.example.smsdiscountorganizer.domain.offer.OfferCategory
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.util.Locale

class ProductCategoryExtractor(
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val terms = linkedMapOf(
        OfferCategory.Apparel to listOf("پوشاک", "لباس", "کفش", "کیف"),
        OfferCategory.Grocery to listOf("مواد غذایی", "سوپرمارکت", "خواربار", "میوه"),
        OfferCategory.Electronics to listOf("گوشی", "موبایل", "لپتاپ", "لپ تاپ", "لوازم الکترونیکی"),
        OfferCategory.Beauty to listOf("آرایشی", "بهداشتی", "زیبایی", "عطر"),
        OfferCategory.Food to listOf("رستوران", "فست فود", "کافه", "غذای رستوران"),
        OfferCategory.Home to listOf("لوازم خانگی", "مبلمان", "دکوراسیون", "لوازم خانه"),
        OfferCategory.Travel to listOf("هتل", "سفر", "پرواز", "بلیط هواپیما"),
        OfferCategory.Entertainment to listOf("سینما", "کنسرت", "تئاتر"),
    )
    private val parentTerms = linkedMapOf(
        OfferCategory.SHOPPING to listOf("مرکز خرید", "مراکز خرید"),
        OfferCategory.FOOD_AND_DRINK to listOf("خوراک و نوشیدنی", "غذا و نوشیدنی"),
    )
    private val generalTerms = listOf("همه محصولات", "تمام محصولات", "همه کالاها", "تمام کالاها", "برای همه خریدها")

    fun extractAll(text: String): List<OfferCategory> {
        val normalized = normalizer.normalize(text).lowercase(Locale.ROOT)
        val leafMatches = terms.filterValues { words -> words.any { term -> containsTerm(normalized, term) } }.keys
        val parentMatches = parentTerms.filterValues { words -> words.any { term -> containsTerm(normalized, term) } }.keys
        val matches = (leafMatches + parentMatches).filterNot { candidate ->
            candidate != OfferCategory.GENERAL && candidate != OfferCategory.UNKNOWN &&
                leafMatches.any { it.isDescendantOf(candidate) }
        }
        if (matches.isEmpty()) {
            return if (generalTerms.any { containsTerm(normalized, it) }) {
                listOf(OfferCategory.GENERAL)
            } else {
                listOf(OfferCategory.UNKNOWN)
            }
        }
        return matches.sortedBy(OfferCategory::stableId)
    }

    /** Compatibility projection for older call sites and stored ProductCategory values. */
    fun extract(text: String): ProductCategory? = extractAll(text).singleOrNull()?.let { category ->
        ProductCategory.entries.firstOrNull { it.name.equals(category.stableId, ignoreCase = true) }
    }

    private fun containsTerm(text: String, term: String): Boolean {
        val normalizedTerm = normalizer.normalize(term).lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()
        val termPattern = normalizedTerm.split(' ').joinToString("[\\s\\u200C]+") { Regex.escape(it) }
        return Regex("(?<![\\p{L}\\p{N}\\u200C])$termPattern(?![\\p{L}\\p{N}\\u200C])")
            .containsMatchIn(text)
    }

    private fun OfferCategory.isDescendantOf(ancestor: OfferCategory): Boolean {
        var parent = parentStableId?.let(OfferCategory::fromStableId)
        while (parent != null) {
            if (parent == ancestor) return true
            parent = parent.parentStableId?.let(OfferCategory::fromStableId)
        }
        return false
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
