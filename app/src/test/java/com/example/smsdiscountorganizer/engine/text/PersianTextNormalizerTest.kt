package com.example.smsdiscountorganizer.engine.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PersianTextNormalizerTest(
    private val input: String,
    private val expected: String,
) {
    private val normalizer = PersianTextNormalizer()

    @Test
    fun normalizesDeterministicallyWithoutLosingMeaningfulText() {
        val first = normalizer.normalize(input)
        val second = normalizer.normalize(input)

        assertEquals(expected, first)
        assertEquals(first, second)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): Collection<Array<Any>> = listOf(
            arrayOf("كالا يكي English", "کالا یکی English"),
            arrayOf("۱۲٣ / ١٢٣", "123 / 123"),
            arrayOf("می\u200cخواهم", "می\u200cخواهم"),
            arrayOf("می\u200bخواهم", "میخواهم"),
            arrayOf("مبلغ ۲۵۰۰ تومن و ۳ ریال", "مبلغ 2500 تومان و 3 ریال"),
            arrayOf("۱۰٪ ، ٢٠ % ، 5﹪", "10% ، 20% ، 5%"),
            arrayOf("کد تخفیف: AB12-X_يك۱۲", "کد تخفیف: AB12-X_يك۱۲"),
            arrayOf("کد: AB\u200B12", "کد: AB\u200B12"),
            arrayOf("کد: AB,CD%۱۲", "کد: AB,CD%۱۲"),
            arrayOf("", ""),
            arrayOf(" \t\u200B ", ""),
            arrayOf("bad\uD800 ١", "bad\uD800 1"),
        )
    }
}

class PersianTextNormalizerSemanticTest {
    @Test
    fun detailedResultKeepsOriginalTextAndDistinctCurrencySemantics() {
        val original = "۵۰۰ تومن / ۵۰۰ ریال"

        val result = PersianTextNormalizer().normalizeDetailed(original)

        val currencies = result.tokens.filter { it.kind == TextToken.Kind.CurrencyAmount }
        assertEquals(original, result.original)
        assertEquals("500 تومان / 500 ریال", result.normalized)
        assertEquals(listOf("500", "500"), currencies.mapNotNull(TextToken::amount))
        assertEquals(listOf(CurrencyUnit.Toman, CurrencyUnit.Rial), currencies.mapNotNull(TextToken::currencyUnit))
        assertEquals(listOf("۵۰۰ تومن", "۵۰۰ ریال"), currencies.map(TextToken::original))
    }

    @Test
    fun couponCodeCharactersAreNotNormalized() {
        val code = "AB12-X_يك۱۲"

        val result = PersianTextNormalizer().normalizeDetailed("کد تخفیف: $code")

        val coupon = result.tokens.single { it.kind == TextToken.Kind.CouponCode }
        assertEquals(code, coupon.original)
        assertEquals(code, coupon.normalized)
        assertFalse(result.normalized.endsWith("AB12-X_يك12"))
        assertTrue(result.normalized.endsWith(code))
    }

    @Test
    fun digitNormalizerConvertsBothArabicDigitSets() {
        assertEquals("0123456789", DigitNormalizer.normalize("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0123456789", DigitNormalizer.normalize("٠١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun currencyNormalizerRecognizesTomanAndRialWithoutConflatingThem() {
        assertEquals("100 تومان و 200 ریال", CurrencyNormalizer().normalize("۱۰۰ تومن و ۲۰۰ ريال"))
    }

    @Test
    fun tokenUtilitiesMarkCouponCurrencyAndPercentageTokens() {
        val tokens = TextTokenUtils.tokenize("کد: SAVE10، ۲۰۰ تومان، ۵٪")

        assertEquals(1, tokens.count { it.kind == TextToken.Kind.CouponCode })
        assertEquals(1, tokens.count { it.kind == TextToken.Kind.CurrencyAmount })
        assertEquals(1, tokens.count { it.kind == TextToken.Kind.Percentage })
    }
}
