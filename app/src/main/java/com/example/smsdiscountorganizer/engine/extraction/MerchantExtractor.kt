package com.example.smsdiscountorganizer.engine.extraction

class MerchantExtractor {
    private val labeledPattern = Regex("(?:نام\\s*(?:فروشگاه|برند)|فروشگاه|برند)\\s*[:：]?\\s*([^؛;،,\\n.!؟]+)")
    private val genericNames = setOf("ما", "این", "آن", "خود", "اینترنتی ما", "اینترنتی", "جدید ما", "آنلاین ما")
    private val stopWords = listOf("کد تخفیف", "تخفیف", "ارسال رایگان", "عنوان پیشنهاد", "جشنواره", "حراج")

    fun extract(text: String): String? {
        val match = labeledPattern.find(text) ?: return null
        var candidate = match.groupValues[1].trim()
        stopWords.forEach { stop ->
            val boundary = candidate.indexOf(stop)
            if (boundary >= 0) candidate = candidate.substring(0, boundary).trim()
        }
        val numericDiscountBoundary = Regex("\\s+[0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?\\s*(?:%|٪|درصد|تومان|تومن|ریال)")
            .find(candidate)?.range?.first
        if (numericDiscountBoundary != null) candidate = candidate.substring(0, numericDiscountBoundary).trim()
        candidate = candidate.trim(' ', ':', '：', '-', '–')
        if (candidate.isBlank() || candidate in genericNames || candidate.length > 50) return null
        if (candidate.matches(Regex("[\\d\\s%٪.]+"))) return null
        return candidate
    }
}
