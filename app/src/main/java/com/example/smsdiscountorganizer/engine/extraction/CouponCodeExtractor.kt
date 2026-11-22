package com.example.smsdiscountorganizer.engine.extraction

class CouponCodeExtractor {
    private val codePattern = Regex("کد\\s*تخفیف\\s*[:：]?\\s*([A-Za-z][A-Za-z0-9_-]{2,31})")
    private val otpPattern = Regex("رمز\\s*(?:پویا|یک\\s*بار\\s*مصرف|یکبار\\s*مصرف|ورود|عبور)|کد\\s*(?:تایید|تأیید|ورود|امنیتی)|\\botp\\b", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        if (otpPattern.containsMatchIn(text)) return null
        return codePattern.find(text)?.groupValues?.get(1)
    }
}
