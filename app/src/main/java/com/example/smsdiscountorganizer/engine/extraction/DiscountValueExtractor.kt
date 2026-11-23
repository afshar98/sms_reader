package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import com.example.smsdiscountorganizer.engine.text.NumberNormalizer

data class DiscountValues(
    val percentageDiscounts: List<Double>?,
    val fixedDiscounts: List<MonetaryAmount>?,
)

class DiscountValueExtractor(
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
    private val purchaseConditionExtractor: PurchaseConditionExtractor = PurchaseConditionExtractor(normalizer),
) {
    private val percentagePatterns = listOf(
        Regex("($NUMBER)\\s*(?:%|درصد)(?:ی)?(?:\\s*تخفیف)?"),
        Regex("تخفیف\\s*($NUMBER)\\s*(?:%|درصد)(?:ی)?"),
        Regex("درصد\\s*تخفیف\\s*($NUMBER)"),
    )
    private val fixedPatterns = listOf(
        Regex("($NUMBER)\\s*(هزار|میلیون)?\\s*(تومان|تومن|ریال)(?:ی)?\\s*تخفیف"),
        Regex("تخفیف\\s*($NUMBER)\\s*(هزار|میلیون)?\\s*(تومان|تومن|ریال)(?:ی)?"),
    )

    fun extract(text: String): DiscountValues = extractNormalized(normalizer.normalize(text))

    internal fun extractNormalized(normalized: String): DiscountValues {
        val excludedRanges = purchaseConditionExtractor.minimumAmountRanges(normalized)
        val percentageMatches = percentagePatterns.flatMap { pattern ->
            pattern.findAll(normalized).filterNot { match -> excludedRanges.any { it.overlaps(match.range) } }.toList()
        }.distinctBy { it.range }
        val percentages = percentageMatches.mapNotNull { match ->
            NumberNormalizer.parse(match.groupValues[1])?.toDouble()
        }
        val fixedMatches = fixedPatterns.flatMap { pattern ->
            pattern.findAll(normalized).filterNot { match -> excludedRanges.any { it.overlaps(match.range) } }.toList()
        }.distinctBy { it.range }
        val fixed = fixedMatches.mapNotNull { match ->
            AmountParser.parse(
                amountText = match.groupValues[1],
                multiplierText = match.groupValues[2].ifBlank { null },
                currencyText = match.groupValues[3],
            )
        }.distinct()
        return DiscountValues(percentages.takeIf(List<Double>::isNotEmpty), fixed.takeIf(List<MonetaryAmount>::isNotEmpty))
    }

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    private companion object {
        const val NUMBER = "[0-9]+(?:[0-9,٬،٫.]*[0-9])?"
    }
}
