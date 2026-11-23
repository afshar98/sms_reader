package com.example.smsdiscountorganizer.engine.extraction

class MerchantExtractor(
    private val registry: MerchantRegistry = MerchantRegistry(),
) {
    private val labeledPattern = Regex(
        "(?:نام\\s*فروشگاه|فروشگاه)(?![\\p{L}\\p{N}])\\s*[:：]?\\s*([^؛;،,\\n.!؟]+)",
    )
    private val campaignBrandPattern = Regex(
        "(?:نام\\s*برند|برند)(?![\\p{L}\\p{N}])\\s*[:：]?\\s*([^؛;،,\\n.!؟]+)",
    )
    private val stopWords = listOf("کد تخفیف", "تخفیف", "ارسال رایگان", "عنوان پیشنهاد", "جشنواره", "حراج", "برند")

    fun extract(text: String): String? = resolve(text)?.merchantName

    fun resolveCampaignBrand(text: String): String? {
        val labeled = campaignBrandPattern.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::cleanLabel)?.let(registry::canonicalize) }
            .toSet()
        return (labeled + registry.campaignBrandMentions(text)).singleOrNull()
    }

    internal fun resolveCampaignBrandNormalized(text: String): String? {
        val labeled = campaignBrandPattern.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::cleanLabel)?.let(registry::canonicalize) }
            .toSet()
        return (labeled + registry.campaignBrandMentionsNormalized(text)).singleOrNull()
    }

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

    /** All merchant names present as explicit labels or complete registry aliases. */
    fun extractEvidenceNames(text: String): Set<String> = buildSet {
        addAll(registry.bodyMentions(text))
        labeledPattern.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.let(::cleanLabel)?.let { add(registry.canonicalize(it)) }
        }
    }

    /** Current-message evidence has precedence over sender history. */
    fun resolveCurrent(text: String): CurrentMerchantEvidence {
        val labels = labeledPattern.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::cleanLabel)?.let(registry::canonicalize) }
            .toSet()
        if (labels.size == 1) return CurrentMerchantEvidence.Identified(labels.single())
        if (labels.size > 1) return CurrentMerchantEvidence.Ambiguous

        val mentions = registry.bodyMentions(text)
        return when (mentions.size) {
            0 -> CurrentMerchantEvidence.None
            1 -> CurrentMerchantEvidence.Identified(mentions.single())
            else -> CurrentMerchantEvidence.Ambiguous
        }
    }

    internal fun resolveCurrentNormalized(text: String): CurrentMerchantEvidence {
        val labels = labeledPattern.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::cleanLabel)?.let(registry::canonicalize) }
            .toSet()
        if (labels.size == 1) return CurrentMerchantEvidence.Identified(labels.single())
        if (labels.size > 1) return CurrentMerchantEvidence.Ambiguous

        val mentions = registry.bodyMentionsNormalized(text)
        return when (mentions.size) {
            0 -> CurrentMerchantEvidence.None
            1 -> CurrentMerchantEvidence.Identified(mentions.single())
            else -> CurrentMerchantEvidence.Ambiguous
        }
    }

    private fun cleanLabel(raw: String): String? {
        var candidate = raw.trim()
        stopWords.forEach { stop ->
            val boundary = candidate.indexOf(stop)
            if (boundary >= 0) candidate = candidate.substring(0, boundary).trim()
        }
        val numericBenefitBoundary = NUMERIC_BENEFIT.find(candidate)?.range?.first
        if (numericBenefitBoundary != null) candidate = candidate.substring(0, numericBenefitBoundary).trim()
        candidate = candidate.trim(' ', ':', '：', '-', '–')
        if (candidate.isBlank() || candidate.length > MAX_MERCHANT_LENGTH) return null
        if (candidate in GENERIC_LABELS) return null
        if (NON_MERCHANT_LABEL.matches(candidate)) return null
        return candidate
    }

    private companion object {
        const val MAX_MERCHANT_LENGTH = 50
        val GENERIC_LABELS = setOf("ما", "این", "آن", "خود", "اینترنتی ما", "آنلاین ما", "فروشگاه ما")
        private val NUMERIC_BENEFIT = Regex("\\s+[0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?\\s*(?:%|٪|درصد|تومان|تومن|ریال)")
        private val NON_MERCHANT_LABEL = Regex("[\\d\\s%٪.]+")
    }
}

sealed interface CurrentMerchantEvidence {
    data object None : CurrentMerchantEvidence
    data object Ambiguous : CurrentMerchantEvidence
    data class Identified(val merchantName: String) : CurrentMerchantEvidence
}
