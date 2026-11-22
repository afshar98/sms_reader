package com.example.smsdiscountorganizer.engine.text

class PersianTextNormalizer(
    private val currencyNormalizer: CurrencyNormalizer = CurrencyNormalizer(),
) {
    fun normalize(input: String): String = normalizeDetailed(input).normalized

    fun normalizeDetailed(input: String): NormalizedPersianText {
        val tokens = TextTokenUtils.tokenize(input).map(currencyNormalizer::normalizeToken)
        val normalized = normalizeWhitespaceAndZeroWidth(tokens)
        return NormalizedPersianText(
            original = input,
            normalized = normalized,
            tokens = tokens,
        )
    }

    private fun normalizeWhitespaceAndZeroWidth(tokens: List<TextToken>): String = buildString {
        var pendingSpace = false
        var previousWasZwnj = false
        tokens.forEach { token ->
            val isCouponCode = token.kind == TextToken.Kind.CouponCode
            token.normalized.forEach { character ->
                when {
                    !isCouponCode && character in REMOVED_ZERO_WIDTH_CHARACTERS -> Unit
                    !isCouponCode && character == '\u200C' -> {
                        if (!previousWasZwnj) append(character)
                        previousWasZwnj = true
                    }
                    !isCouponCode && (character.isWhitespace() || Character.isSpaceChar(character)) -> {
                        pendingSpace = isNotEmpty()
                        previousWasZwnj = false
                    }
                    else -> {
                        if (pendingSpace) append(' ')
                        append(character)
                        pendingSpace = false
                        previousWasZwnj = !isCouponCode && character == '\u200C'
                    }
                }
            }
        }
    }

    private companion object {
        val REMOVED_ZERO_WIDTH_CHARACTERS = setOf(
            '\u200B', // Zero Width Space.
            '\u200D', // Zero Width Joiner.
            '\u2060', // Word Joiner.
            '\uFEFF', // Zero Width No-Break Space / BOM.
            '\u200E', // Left-to-right mark.
            '\u200F', // Right-to-left mark.
        )
    }
}
