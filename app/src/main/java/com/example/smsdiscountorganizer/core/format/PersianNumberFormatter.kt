package com.example.smsdiscountorganizer.core.format

import com.example.smsdiscountorganizer.engine.text.NumberNormalizer
import java.math.BigDecimal

/** Persian UI formatting kept separate from engine parsing and stored values. */
object PersianNumberFormatter {
    private val numberPattern = Regex("(?<![\\p{L}\\p{N}_-])[0-9٠-٩۰-۹][0-9٠-٩۰-۹,٬،٫.]*")

    fun formatNumber(value: Number): String = formatNumber(value.toString())

    fun formatNumber(value: String): String {
        val parsed = NumberNormalizer.parse(value) ?: return localizeDigits(value)
        val plain = parsed.stripTrailingZeros().toPlainString()
        val sign = if (plain.startsWith('-')) "−" else ""
        val unsigned = plain.removePrefix("-").removePrefix("+")
        val parts = unsigned.split('.', limit = 2)
        val grouped = parts[0].reversed().chunked(3).joinToString("٬").reversed()
        val formatted = sign + grouped + parts.getOrNull(1)?.let { "٫$it" }.orEmpty()
        return localizeDigits(formatted)
    }

    fun formatPercent(value: Number): String = "${formatNumber(value)}٪"

    /** Formats numeric fragments in labels while leaving identifier-like tokens intact. */
    fun formatNumericText(value: String): String {
        val normalized = NumberNormalizer.normalizeNumericText(value)
        return numberPattern.replace(normalized) { match -> formatNumber(match.value) }
            .replace('%', '٪')
    }

    fun localizeDigits(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when {
                character in '0'..'9' -> append('۰' + (character - '0'))
                character == '%' -> append('٪')
                else -> append(character)
            }
        }
    }
}
