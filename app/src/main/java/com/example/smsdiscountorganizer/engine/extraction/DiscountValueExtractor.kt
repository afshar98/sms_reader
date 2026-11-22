package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer

data class DiscountValues(
    val percentageDiscounts: List<Double>?,
    val fixedDiscounts: List<MonetaryAmount>?,
)

class DiscountValueExtractor(
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
    private val purchaseConditionExtractor: PurchaseConditionExtractor = PurchaseConditionExtractor(normalizer),
) {
    private val percentagePatterns = listOf(
        Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:%|درصد)(?:ی)?(?:\\s*تخفیف)?"),
        Regex("تخفیف\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:%|درصد)(?:ی)?"),
        Regex("درصد\\s*تخفیف\\s*(\\d+(?:[.,]\\d+)?)"),
    )
    private val fixedPatterns = listOf(
        Regex("(\\d+(?:[.,]\\d+)?)\\s*(هزار|میلیون)?\\s*(تومان|تومن|ریال)(?:ی)?\\s*تخفیف"),
        Regex("تخفیف\\s*(\\d+(?:[.,]\\d+)?)\\s*(هزار|میلیون)?\\s*(تومان|تومن|ریال)(?:ی)?"),
    )

    fun extract(text: String): DiscountValues {
        val normalized = normalizer.normalize(text)
        val excludedRanges = purchaseConditionExtractor.minimumAmountRanges(normalized)
        val percentageMatches = percentagePatterns.flatMap { pattern ->
            pattern.findAll(normalized).filterNot { match -> excludedRanges.any { it.overlaps(match.range) } }.toList()
        }.distinctBy { it.range }
        val percentages = percentageMatches.mapNotNull { match ->
            match.groupValues[1].replace(',', '.').toDoubleOrNull()
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
}
