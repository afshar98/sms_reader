package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.engine.text.NumberNormalizer

internal object AmountParser {
    fun parse(amountText: String, multiplierText: String?, currencyText: String): MonetaryAmount? {
        val amount = NumberNormalizer.parse(amountText) ?: return null
        val multiplier = when (multiplierText) {
            "هزار" -> 1_000L
            "میلیون" -> 1_000_000L
            else -> 1L
        }
        val normalized = amount.multiply(multiplier.toBigDecimal()).stripTrailingZeros().toPlainString()
        val currency = when (currencyText) {
            "تومان", "تومن" -> MoneyCurrency.Toman
            "ریال" -> MoneyCurrency.Rial
            else -> return null
        }
        return MonetaryAmount(normalized, currency)
    }
}
