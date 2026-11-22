package com.example.smsdiscountorganizer.engine.text

class CurrencyNormalizer {
    fun normalize(input: String): String = TextTokenUtils.tokenize(input)
        .joinToString(separator = "") { normalizeToken(it).normalized }

    internal fun normalizeToken(token: TextToken): TextToken {
        val normalized = when (token.kind) {
            TextToken.Kind.CouponCode -> token.original
            TextToken.Kind.CurrencyAmount -> {
                val amount = normalizeNumber(token.amount.orEmpty())
                "$amount ${token.currencyUnit?.canonicalText.orEmpty()}"
            }
            TextToken.Kind.CurrencyUnit -> token.currencyUnit?.canonicalText ?: normalizePlainText(token.original)
            TextToken.Kind.Percentage -> "${normalizeNumber(token.amount.orEmpty())}%"
            TextToken.Kind.Text -> normalizePlainText(token.original)
        }
        return token.copy(
            normalized = normalized,
            amount = token.amount?.let(::normalizeNumber),
        )
    }

    internal fun normalizePlainText(input: String): String = TextTokenUtils.normalizeArabicLetterVariants(
        DigitNormalizer.normalize(input),
    ).map { character ->
        when (character) {
            '٪', '﹪' -> '%'
            else -> character
        }
    }.joinToString(separator = "")

    private fun normalizeNumber(value: String): String =
        DigitNormalizer.normalize(value).replace('٫', '.')
}
