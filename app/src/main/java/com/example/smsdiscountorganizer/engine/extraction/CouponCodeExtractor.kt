package com.example.smsdiscountorganizer.engine.extraction

class CouponCodeExtractor {
    private val codePatterns = listOf(
        Regex("(?:کد\\s*تخفیف(?:\\s*شما)?|با\\s*کد)\\s*[:：]?\\s*([A-Za-z0-9_-]{3,32})", RegexOption.IGNORE_CASE),
        Regex("(?:^|[\\s\\n])کد\\s*[:：]\\s*([A-Za-z0-9_-]{3,32})", RegexOption.IGNORE_CASE),
    )
    private val otpPattern = Regex("رمز\\s*(?:پویا|یک\\s*بار\\s*مصرف|یکبار\\s*مصرف|ورود|عبور)|کد\\s*(?:تایید|تأیید|ورود|امنیتی)|\\botp\\b", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        if (otpPattern.containsMatchIn(text)) return null
        return codePatterns.asSequence()
            .mapNotNull { pattern -> pattern.find(text)?.groupValues?.getOrNull(1) }
            .firstOrNull { code -> code.any(Char::isLetter) }
    }
}
