package com.example.smsdiscountorganizer.engine.extraction

class MerchantExtractor(
    private val registry: MerchantRegistry = MerchantRegistry(),
) {
    private val labeledPattern = Regex(
        "(?:نام\\s*(?:فروشگاه|برند)|فروشگاه|برند)(?![\\p{L}\\p{N}])\\s*[:：]?\\s*([^؛;،,\\n.!؟]+)",
    )
    private val stopWords = listOf("کد تخفیف", "تخفیف", "ارسال رایگان", "عنوان پیشنهاد", "جشنواره", "حراج")

    fun extract(text: String): String? = resolve(text)?.merchantName

    fun resolve(
        text: String,
        senderAddress: String? = null,
        manualCorrection: String? = null,
    ): MerchantMatch? {
        val explicit = labeledPattern.find(text)?.groupValues?.getOrNull(1)?.let(::cleanLabel)
        return registry.resolve(
            text = text,
            senderAddress = senderAddress,
            manualCorrection = manualCorrection,
            explicitMerchant = explicit,
        )
    }

    private fun cleanLabel(raw: String): String? {
        var candidate = raw.trim()
        stopWords.forEach { stop ->
            val boundary = candidate.indexOf(stop)
            if (boundary >= 0) candidate = candidate.substring(0, boundary).trim()
        }
        val numericBenefitBoundary = Regex(
            "\\s+[0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?\\s*(?:%|٪|درصد|تومان|تومن|ریال)",
        ).find(candidate)?.range?.first
        if (numericBenefitBoundary != null) candidate = candidate.substring(0, numericBenefitBoundary).trim()
        candidate = candidate.trim(' ', ':', '：', '-', '–')
        if (candidate.isBlank() || candidate.length > MAX_MERCHANT_LENGTH) return null
        if (candidate.matches(Regex("[\\d\\s%٪.]+"))) return null
        return candidate
    }

    private companion object {
        const val MAX_MERCHANT_LENGTH = 50
    }
}
