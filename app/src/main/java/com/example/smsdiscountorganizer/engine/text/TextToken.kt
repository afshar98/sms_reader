package com.example.smsdiscountorganizer.engine.text

enum class CurrencyUnit(val canonicalText: String) {
    Toman("تومان"),
    Rial("ریال"),
}

data class TextToken(
    val original: String,
    val normalized: String = original,
    val kind: Kind = Kind.Text,
    val amount: String? = null,
    val currencyUnit: CurrencyUnit? = null,
    val startIndex: Int = 0,
    val endIndexExclusive: Int = startIndex + original.length,
) {
    enum class Kind {
        Text,
        CouponCode,
        CurrencyAmount,
        CurrencyUnit,
        Percentage,
    }
}

data class NormalizedPersianText(
    val original: String,
    val normalized: String,
    val tokens: List<TextToken>,
)
