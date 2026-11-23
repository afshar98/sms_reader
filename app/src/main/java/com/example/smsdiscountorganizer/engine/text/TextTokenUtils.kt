package com.example.smsdiscountorganizer.engine.text

/** Finds semantic spans while retaining source ranges and the exact original token text. */
object TextTokenUtils {
    private val couponCodePattern = Regex(
        "(?:کد(?:‌?\\s*تخفیف)?|coupon(?:\\s+code)?|code)\\s*(?:[:：=#-]\\s*)?([^\\s]+)",
        RegexOption.IGNORE_CASE,
    )
    private val currencyAmountPattern = Regex(
        "(?<![\\p{L}\\p{N}])([+\\-]?[0-9٠-٩۰-۹][0-9٠-٩۰-۹,٬،٫.\\s]*)(\\s*)(تومان|تومن|ریال|ريال)(?![\\p{L}\\p{N}])",
    )
    private val percentagePattern = Regex(
        "(?<![\\p{L}\\p{N}])([+\\-]?[0-9٠-٩۰-۹][0-9٠-٩۰-۹,٬،٫.\\s]*)\\s*([%٪﹪])",
    )
    private val currencyUnitPattern = Regex("(?<![\\p{L}\\p{N}])(تومان|تومن|ریال|ريال)(?![\\p{L}\\p{N}])")

    fun tokenize(input: String): List<TextToken> {
        if (input.isEmpty()) return emptyList()

        val semanticTokens = buildList {
            couponCodePattern.findAll(input).forEach { match ->
                val code = match.groups[1] ?: return@forEach
                add(
                    token(
                        input = input,
                        start = code.range.first,
                        endExclusive = code.range.last + 1,
                        kind = TextToken.Kind.CouponCode,
                    ),
                )
            }

            currencyAmountPattern.findAll(input).forEach { match ->
                val amount = match.groups[1] ?: return@forEach
                val unit = match.groups[3]?.value?.let(::currencyUnit) ?: return@forEach
                val start = match.range.first
                val endExclusive = match.range.last + 1
                if (overlapsExisting(this, start, endExclusive)) return@forEach
                add(
                    token(
                        input = input,
                        start = start,
                        endExclusive = endExclusive,
                        kind = TextToken.Kind.CurrencyAmount,
                        amount = amount.value,
                        currencyUnit = unit,
                    ),
                )
            }

            percentagePattern.findAll(input).forEach { match ->
                val amount = match.groups[1] ?: return@forEach
                val start = match.range.first
                val endExclusive = match.range.last + 1
                if (overlapsExisting(this, start, endExclusive)) return@forEach
                add(
                    token(
                        input = input,
                        start = start,
                        endExclusive = endExclusive,
                        kind = TextToken.Kind.Percentage,
                        amount = amount.value,
                    ),
                )
            }

            currencyUnitPattern.findAll(input).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1
                if (overlapsExisting(this, start, endExclusive)) return@forEach
                add(
                    token(
                        input = input,
                        start = start,
                        endExclusive = endExclusive,
                        kind = TextToken.Kind.CurrencyUnit,
                        currencyUnit = currencyUnit(match.value),
                    ),
                )
            }
        }

        val ordered = semanticTokens.sortedWith(compareBy(TextToken::startIndex, TextToken::endIndexExclusive))
        return buildList {
            var position = 0
            ordered.forEach { semanticToken ->
                if (semanticToken.startIndex < position) return@forEach
                if (semanticToken.startIndex > position) {
                    add(token(input, position, semanticToken.startIndex, TextToken.Kind.Text))
                }
                add(semanticToken)
                position = semanticToken.endIndexExclusive
            }
            if (position < input.length) add(token(input, position, input.length, TextToken.Kind.Text))
        }
    }

    internal fun normalizeArabicLetterVariants(input: String): String = buildString(input.length) {
        input.forEach { character ->
            append(
                when (character) {
                    '\u064A', '\u0649' -> 'ی' // Arabic Yeh and Alef Maksura.
                    '\u0643' -> 'ک' // Arabic Kaf.
                    else -> character
                },
            )
        }
    }

    internal fun currencyUnit(value: String): CurrencyUnit? = when (normalizeArabicLetterVariants(value)) {
        "تومان", "تومن" -> CurrencyUnit.Toman
        "ریال" -> CurrencyUnit.Rial
        else -> null
    }

    private fun token(
        input: String,
        start: Int,
        endExclusive: Int,
        kind: TextToken.Kind,
        amount: String? = null,
        currencyUnit: CurrencyUnit? = null,
    ): TextToken {
        val original = input.substring(start, endExclusive)
        return TextToken(
            original = original,
            kind = kind,
            amount = amount,
            currencyUnit = currencyUnit,
            startIndex = start,
            endIndexExclusive = endExclusive,
        )
    }

    private fun overlapsExisting(tokens: List<TextToken>, start: Int, endExclusive: Int): Boolean =
        tokens.any { start < it.endIndexExclusive && endExclusive > it.startIndex }
}
