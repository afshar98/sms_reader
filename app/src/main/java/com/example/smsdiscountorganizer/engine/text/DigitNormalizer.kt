package com.example.smsdiscountorganizer.engine.text

/** Converts Persian and Arabic-Indic decimal digits to their ASCII equivalents. */
object DigitNormalizer {
    fun normalize(input: String): String = NumberNormalizer.normalizeDigits(input)
}
