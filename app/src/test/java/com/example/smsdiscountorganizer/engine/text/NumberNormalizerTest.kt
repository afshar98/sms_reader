package com.example.smsdiscountorganizer.engine.text

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizerTest {
    @Test
    fun convertsAllDigitSystemsAndMixedScripts() {
        assertEquals("0123456789", NumberNormalizer.normalizeDigits("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0123456789", NumberNormalizer.normalizeDigits("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("1203", NumberNormalizer.normalizeDigits("12۰۳"))
        assertEquals(BigDecimal("1203"), NumberNormalizer.parse("12۰۳"))
    }

    @Test
    fun parsesThousandsAndExplicitDecimalSeparators() {
        assertEquals("150000", NumberNormalizer.normalizeNumericValue("۱۵۰٬۰۰۰"))
    }

    @Test
    fun parsesPersianArabicAndLatinExamplesConsistently() {
        listOf("۱۵۰٬۰۰۰", "١٥٠,٠٠٠", "150,000").forEach { input ->
            assertEquals(BigDecimal("150000"), NumberNormalizer.parse(input))
        }
        listOf("12٫5", "١٢,٥", "12.5").forEach { input ->
            assertEquals(BigDecimal("12.5"), NumberNormalizer.parse(input))
        }
        assertEquals(BigDecimal("150000"), NumberNormalizer.parse("150\u00A0000"))
    }

    @Test
    fun rejectsAmbiguousOrMalformedGroupingInsteadOfGuessing() {
        assertNull(NumberNormalizer.parse("1,23,456"))
        assertNull(NumberNormalizer.parse("1..2"))
        assertNull(NumberNormalizer.parse("1 2"))
    }

    @Test
    fun normalizesNumericTextWithoutChangingCouponIdentifiers() {
        assertEquals("مبلغ 150000 و 80%؛ کد SAVE20", NumberNormalizer.normalizeNumericText("مبلغ ١٥٠,٠٠٠ و ۸۰٪؛ کد SAVE20"))
    }
}
