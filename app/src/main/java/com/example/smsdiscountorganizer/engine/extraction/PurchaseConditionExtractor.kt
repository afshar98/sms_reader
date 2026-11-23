package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer

class PurchaseConditionExtractor(
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val minimumPattern = Regex(
        "(?:حداقل\\s*(?:مبلغ\\s*)?(?:خرید|سفارش)|(?:خرید|سفارش)\\s*(?:بالای|بیش\\s*از|حداقل))" +
            "\\s*[:：]?\\s*([0-9]+(?:[0-9,٬،٫.]*[0-9])?)\\s*(هزار|میلیون)?\\s*(تومان|تومن|ریال)",
    )

    fun extractMinimumPurchase(text: String): MonetaryAmount? {
        val normalized = normalizer.normalize(text)
        val match = minimumPattern.find(normalized) ?: return null
        return AmountParser.parse(match.groupValues[1], match.groupValues[2].ifBlank { null }, match.groupValues[3])
    }

    internal fun minimumAmountRanges(normalizedText: String): List<IntRange> =
        minimumPattern.findAll(normalizedText).map { it.range }.toList()
}
