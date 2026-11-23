package com.example.smsdiscountorganizer.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianNumberFormatterTest {
    @Test
    fun formatsIntegersDecimalsAndPercentagesForPersianUi() {
        assertEquals("۳٬۰۰۰", PersianNumberFormatter.formatNumber(3000))
        assertEquals("۲۸۶", PersianNumberFormatter.formatNumber(286))
        assertEquals("۲٬۱۴۷٬۴۸۳٬۶۴۷", PersianNumberFormatter.formatNumber(Int.MAX_VALUE))
        assertEquals("۱۵۰٬۰۰۰", PersianNumberFormatter.formatNumber("150000"))
        assertEquals("۱۲٫۵", PersianNumberFormatter.formatNumber("12.5"))
        assertEquals("۸۰٪", PersianNumberFormatter.formatPercent(80))
    }

    @Test
    fun formatsCountLabelsAndLeavesCouponIdentifiersUntouched() {
        assertEquals("۱٬۲۳۴٬۵۶۷ پیام بررسی شد؛ ۸۰٪ تخفیف", PersianNumberFormatter.formatNumericText("1234567 پیام بررسی شد؛ 80% تخفیف"))
        assertEquals("کد SAVE20 و xyzp و OFF123", PersianNumberFormatter.formatNumericText("کد SAVE20 و xyzp و OFF123"))
    }
}
