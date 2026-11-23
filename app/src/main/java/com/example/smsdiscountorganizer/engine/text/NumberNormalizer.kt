package com.example.smsdiscountorganizer.engine.text

import java.math.BigDecimal

/** Canonicalizes numeric values without changing surrounding text or identifiers. */
object NumberNormalizer {
    private val numericTextPattern = Regex("(?<![\\p{L}\\p{N}_-])[0-9٠-٩۰-۹][0-9٠-٩۰-۹,٬،٫.\\u00A0\\u202F\\s]*(?![\\p{L}\\p{N}_-])")

    fun normalizeDigits(input: String): String = buildString(input.length) {
        input.forEach { character ->
            append(
                when (character) {
                    in '۰'..'۹' -> '0' + (character - '۰')
                    in '٠'..'٩' -> '0' + (character - '٠')
                    else -> character
                },
            )
        }
    }

    /**
     * Parses Persian and Latin formatted values using explicit separator rules.
     * Persian ٫ is decimal and ٬ is grouping. Comma, Arabic comma and dot are
     * grouping when valid three-digit groups; a single non-grouping separator
     * is decimal. Ambiguous mixed separators are rejected.
     */
    fun parse(input: String): BigDecimal? {
        val digits = normalizeDigits(input).trim()
        if (digits.isEmpty()) return null
        val sign = if (digits.first() == '-' || digits.first() == '+') digits.first().toString() else ""
        val unsigned = if (sign.isEmpty()) digits else digits.drop(1)
        if (unsigned.isEmpty() || unsigned.any { !it.isDigit() && it !in SEPARATORS && !it.isWhitespace() && !Character.isSpaceChar(it) }) return null
        val digitGroups = Regex("(?<=\\d)[\\s\\u00A0\\u202F]+(?=\\d)").containsMatchIn(unsigned)
        if (digitGroups) {
            val groups = unsigned.trim().split(Regex("[\\s\\u00A0\\u202F]+"))
            if (groups.size < 2 || groups.any { !it.all(Char::isDigit) } || groups.first().length !in 1..3 ||
                groups.drop(1).any { it.length != 3 }
            ) return null
        }
        val compact = unsigned.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
        if (compact.isEmpty()) return null

        val persianDecimals = compact.count { it == '٫' }
        if (persianDecimals > 1) return null
        val canonical = if (persianDecimals == 1) {
            val parts = compact.split('٫')
            if (parts.size != 2 || !parts[0].isValidIntegerPart() || parts[1].isEmpty() || !parts[1].all(Char::isDigit)) return null
            parts[0].removeGroupingSeparators() + "." + parts[1]
        } else {
            normalizeCommonSeparators(compact) ?: return null
        }
        return (sign + canonical).toBigDecimalOrNull()
    }

    fun normalizeNumericValue(input: String): String? = parse(input)?.stripTrailingZeros()?.toPlainString()

    /** Applies canonical number spelling to a display/search fragment; callers keep the source text. */
    fun normalizeNumericText(input: String): String = numericTextPattern.replace(input) { match ->
        val raw = match.value.trimEnd()
        val trailing = match.value.drop(raw.length)
        normalizeNumericValue(raw)?.plus(trailing) ?: match.value
    }.replace('٪', '%').replace('﹪', '%')

    private fun normalizeCommonSeparators(value: String): String? {
        val separators = value.filter { it in COMMON_SEPARATORS }.toSet()
        if (separators.isEmpty()) return if (value.all(Char::isDigit)) value else null
        if (separators.size > 1) {
            val lastIndex = value.indexOfLast { it in COMMON_SEPARATORS }
            val decimal = value[lastIndex]
            if (decimal == '٬' || decimal == '،') return null
            val fraction = value.substring(lastIndex + 1)
            if (fraction.isEmpty() || !fraction.all(Char::isDigit) || fraction.length == 3) return null
            val integer = value.substring(0, lastIndex)
            if (!integer.isValidIntegerPart()) return null
            return integer.removeGroupingSeparators() + "." + fraction
        }
        val separator = separators.single()
        val groups = value.split(separator)
        if (groups.size < 2 || groups.any(String::isEmpty) || groups.any { !it.all(Char::isDigit) }) return null
        if (groups.drop(1).all { it.length == 3 } && groups.first().length in 1..3) {
            return groups.joinToString("")
        }
        if (groups.size == 2 && groups.first().isNotEmpty() && groups.first().all(Char::isDigit)) {
            return groups[0] + "." + groups[1]
        }
        return null
    }

    private fun String.isValidIntegerPart(): Boolean {
        if (isEmpty()) return false
        if (all(Char::isDigit)) return true
        val groupSeparators = filter { it in GROUPING_SEPARATORS }.toSet()
        if (groupSeparators.size != 1 || any { !it.isDigit() && it !in GROUPING_SEPARATORS }) return false
        val groups = split(groupSeparators.single())
        return groups.size >= 2 && groups.first().length in 1..3 && groups.first().all(Char::isDigit) &&
            groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
    }

    private fun String.removeGroupingSeparators(): String = filter { it.isDigit() }

    private val SEPARATORS = setOf(',', '٬', '،', '٫', '.')
    private val COMMON_SEPARATORS = setOf(',', '٬', '،', '.')
    private val GROUPING_SEPARATORS = setOf(',', '٬', '،', '.')
}
