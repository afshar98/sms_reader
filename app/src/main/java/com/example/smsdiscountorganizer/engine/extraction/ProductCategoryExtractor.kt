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
        OfferCategory.Grocery to listOf("مواد غذایی", "سوپرمارکت", "خواربار", "میوه", "تپسی‌مارکت", "تپسی مارکت", "tapsimarket"),
        OfferCategory.Electronics to listOf("گوشی", "موبایل", "لپتاپ", "لپ تاپ", "لوازم الکترونیکی", "هندزفری", "هدفون", "ایرفون", "تبلت"),
        OfferCategory.Beauty to listOf("آرایشی", "بهداشتی", "زیبایی", "عطر"),
        OfferCategory.Food to listOf("رستوران", "فست فود", "کافه", "غذای رستوران"),
        OfferCategory.Home to listOf("لوازم خانگی", "مبلمان", "دکوراسیون", "لوازم خانه"),
        OfferCategory.Jewelry to listOf("پلاک طلا", "زیورآلات", "جواهر", "گردنبند", "انگشتر", "دستبند"),
        OfferCategory.Travel to listOf("هتل", "سفر", "پرواز", "بلیط هواپیما"),
        OfferCategory.Entertainment to listOf("سینما", "کنسرت", "تئاتر"),
    )
    private val parentTerms = linkedMapOf(
        OfferCategory.SHOPPING to listOf("مرکز خرید", "مراکز خرید"),
        OfferCategory.FOOD_AND_DRINK to listOf("خوراک و نوشیدنی", "غذا و نوشیدنی"),
    )
    private val generalTerms = listOf("همه محصولات", "تمام محصولات", "همه کالاها", "تمام کالاها", "برای همه خریدها")

    private val termPatterns: Map<String, Regex> =
        (terms.values.flatten() + parentTerms.values.flatten() + generalTerms).distinct().associateWith { term ->
            val normalizedTerm = normalizer.normalize(term).lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()
            val termPattern = normalizedTerm.split(' ').joinToString("[\\s\\u200C]+") { Regex.escape(it) }
            Regex("(?<![\\p{L}\\p{N}\\u200C])$termPattern(?![\\p{L}\\p{N}\\u200C])")
        }

    fun extractAll(text: String): List<OfferCategory> = extractAllNormalized(normalizer.normalize(text))

    internal fun extractAllNormalized(normalizedText: String): List<OfferCategory> {
        val normalized = normalizedText.lowercase(Locale.ROOT)
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

    /** True when a percentage occurs close to a recognized product or service term. */
    fun hasProductNearPercentage(text: String, maxDistance: Int = 30): Boolean =
        hasProductNearPercentageNormalized(normalizer.normalize(text), maxDistance)

    internal fun hasProductNearPercentageNormalized(normalized: String, maxDistance: Int = 30): Boolean {
        val percentages = PERCENTAGE.findAll(normalized).toList()
        if (percentages.isEmpty()) return false
        val productTerms = terms.values.flatten()
        return productTerms.any { term ->
            wholeTermMatches(normalized, term).any { productRange ->
                percentages.any { percentageRange ->
                    val distance = when {
                        productRange.last < percentageRange.range.first -> percentageRange.range.first - productRange.last - 1
                        percentageRange.range.last < productRange.first -> productRange.first - percentageRange.range.last - 1
                        else -> 0
                    }
                    distance in 0..maxDistance
                }
            }
        }
    }

    /** Compatibility projection for older call sites and stored ProductCategory values. */
    fun extract(text: String): ProductCategory? = extractNormalized(normalizer.normalize(text))

    internal fun extractNormalized(normalizedText: String): ProductCategory? = extractAllNormalized(normalizedText).singleOrNull()?.let { category ->
        ProductCategory.entries.firstOrNull { it.name.equals(category.stableId, ignoreCase = true) }
    }

    private fun containsTerm(text: String, term: String): Boolean = termPatterns.getValue(term).containsMatchIn(text)

    private fun wholeTermMatches(text: String, term: String): List<IntRange> {
        return termPatterns.getValue(term)
            .findAll(text.lowercase(Locale.ROOT))
            .map(MatchResult::range)
            .toList()
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
        val PERCENTAGE = Regex("(?<![0-9])([0-9]+(?:[.,][0-9]+)?)\\s*(?:%|درصد)(?:ی)?")
    }
}
