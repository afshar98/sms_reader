package com.example.smsdiscountorganizer.engine.text

/** Converts Persian and Arabic-Indic decimal digits to their ASCII equivalents. */
object DigitNormalizer {
    fun normalize(input: String): String = buildString(input.length) {
        input.forEach { character ->
            append(
                when (character) {
                    in PERSIAN_DIGITS -> '0' + (character - PERSIAN_DIGITS.first)
                    in ARABIC_INDIC_DIGITS -> '0' + (character - ARABIC_INDIC_DIGITS.first)
                    else -> character
                },
            )
        }
    }

    private val PERSIAN_DIGITS = '۰'..'۹'
    private val ARABIC_INDIC_DIGITS = '٠'..'٩'
}
