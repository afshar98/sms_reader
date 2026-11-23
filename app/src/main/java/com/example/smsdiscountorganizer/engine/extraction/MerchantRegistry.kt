package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.util.Locale

enum class MerchantEvidence {
    MANUAL_CORRECTION,
    EXPLICIT_LABEL,
    BODY_ALIAS,
    SENDER_ID,
}

data class MerchantMatch(
    val merchantName: String,
    val evidence: MerchantEvidence,
)

data class MerchantDefinition(
    val canonicalName: String,
    val aliases: Set<String>,
    val senderAliases: Set<String> = emptySet(),
    val identityRole: MerchantIdentityRole = MerchantIdentityRole.MERCHANT,
)

enum class MerchantIdentityRole { MERCHANT, CAMPAIGN_BRAND }

/** On-device alias registry. Unknown senders and generic gateway IDs are never brands by default. */
class MerchantRegistry(
    definitions: List<MerchantDefinition> = defaultDefinitions(),
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val definitions = definitions.toList()
    private val normalizedAliases = this.definitions.associateWith { definition ->
        (definition.aliases + definition.canonicalName).map { normalize(it) }.filter(String::isNotBlank).distinct()
    }
    private val aliasPatterns = this.definitions.associateWith { definition ->
        normalizedAliases.getValue(definition).map(::wholeAliasPattern)
    }
    private val normalizedSenderAliases = this.definitions.associateWith { definition ->
        definition.senderAliases.mapTo(hashSetOf()) { normalize(it) }
    }

    init {
        require(this.definitions.map { normalize(it.canonicalName) }.distinct().size == this.definitions.size) {
            "Merchant canonical names must be unique after normalization"
        }
    }

    fun resolve(
        text: String,
        senderAddress: String? = null,
        manualCorrection: String? = null,
        explicitMerchant: String? = null,
    ): MerchantMatch? {
        manualCorrection.cleanMerchant()?.let { return MerchantMatch(canonicalize(it), MerchantEvidence.MANUAL_CORRECTION) }
        explicitMerchant.cleanMerchant()?.let { return MerchantMatch(canonicalize(it), MerchantEvidence.EXPLICIT_LABEL) }

        val normalizedText = normalize(text)
        val bodyMatches = definitions.filter { definition ->
            definition.identityRole == MerchantIdentityRole.MERCHANT &&
            aliasPatterns.getValue(definition).any { it.containsMatchIn(normalizedText) }
        }
        if (bodyMatches.size == 1) return MerchantMatch(bodyMatches.single().canonicalName, MerchantEvidence.BODY_ALIAS)
        if (bodyMatches.size > 1) return null

        val sender = senderAddress.cleanMerchant()?.let(::normalize) ?: return null
        if (sender in GENERIC_SENDERS) return null
        val senderMatches = definitions.filter { definition ->
            definition.identityRole == MerchantIdentityRole.MERCHANT &&
                sender in normalizedSenderAliases.getValue(definition)
        }
        return senderMatches.singleOrNull()?.let {
            MerchantMatch(it.canonicalName, MerchantEvidence.SENDER_ID)
        }
    }

    fun canonicalize(value: String): String {
        val normalized = normalize(value)
        return definitions.firstOrNull { definition -> normalized in normalizedAliases.getValue(definition) }
            ?.canonicalName ?: value.trim()
    }

    /** Returns every locally registered brand explicitly named in the supplied message. */
    fun bodyMentions(text: String): Set<String> {
        return bodyMentionsNormalized(normalize(text))
    }

    internal fun bodyMentionsNormalized(normalizedText: String): Set<String> =
        matchingDefinitions(normalizedText, MerchantIdentityRole.MERCHANT)
            .mapTo(linkedSetOf(), MerchantDefinition::canonicalName)

    /** Registered brands are kept separate from merchants and sender profiles. */
    fun campaignBrandMentions(text: String): Set<String> =
        campaignBrandMentionsNormalized(normalize(text))

    internal fun campaignBrandMentionsNormalized(normalizedText: String): Set<String> =
        matchingDefinitions(normalizedText, MerchantIdentityRole.CAMPAIGN_BRAND)
            .mapTo(linkedSetOf(), MerchantDefinition::canonicalName)

    private fun matchingDefinitions(text: String, role: MerchantIdentityRole): List<MerchantDefinition> =
        definitions.filter { definition ->
            definition.identityRole == role &&
                aliasPatterns.getValue(definition).any { it.containsMatchIn(text) }
        }

    private fun normalize(value: String): String =
        normalizer.normalize(value).lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()

    private fun wholeAliasPattern(alias: String): Regex =
        Regex("(?<![\\p{L}\\p{N}\\u200C])${Regex.escape(alias)}(?![\\p{L}\\p{N}\\u200C])")

    private fun String?.cleanMerchant(): String? = this?.trim()?.takeIf {
        it.isNotEmpty() && it.length <= MAX_MERCHANT_LENGTH && it !in GENERIC_LABELS
    }

    companion object {
        private const val MAX_MERCHANT_LENGTH = 50
        private val WHITESPACE = Regex("\\s+")
        private val GENERIC_SENDERS = setOf("sms", "message", "پیامک", "سامانه", "info", "noreply", "no-reply")
        private val GENERIC_LABELS = setOf(
            "ما", "این", "آن", "خود", "اینترنتی ما", "اینترنتی", "جدید ما", "آنلاین ما", "فروشگاه ما",
        )

        fun defaultDefinitions(): List<MerchantDefinition> = listOf(
            MerchantDefinition("دیجی‌کالا", setOf("دیجی کالا", "دیجی‌کالا", "digikala", "digi kala", "digikala.com"), setOf("DIGIKALA")),
            MerchantDefinition(
                "دیجی‌پی",
                setOf("دیجی پی", "دیجی‌پی", "digipay", "digi pay", "digi-pay"),
                identityRole = MerchantIdentityRole.CAMPAIGN_BRAND,
            ),
            MerchantDefinition("اسنپ‌فود", setOf("اسنپ فود", "اسنپ‌فود", "snappfood", "snapp food"), setOf("SNAPPFOOD")),
            MerchantDefinition("اسنپ‌مارکت", setOf("اسنپ مارکت", "اسنپ‌مارکت", "snappmarket", "snapp market"), setOf("SNAPPMARKET")),
            MerchantDefinition("تپسی‌مارکت", setOf("تپسی مارکت", "تپسی‌مارکت", "tapsimarket", "tapsi market")),
            MerchantDefinition("تپسی", setOf("تپسی", "tapsi"), setOf("TAPSI")),
            MerchantDefinition("ترب", setOf("ترب", "torob"), setOf("TOROB")),
            MerchantDefinition("بانی‌مد", setOf("بانی مد", "بانی‌مد", "banimode", "bani mode"), setOf("BANIMODE")),
            MerchantDefinition("خانومی", setOf("خانومی", "khanoumi", "khanomi"), setOf("KANOOMI")),
            MerchantDefinition("اُکالا", setOf("اکالا", "اُکالا", "okala"), setOf("OKALA")),
            MerchantDefinition("دیجی‌استایل", setOf("دیجی استایل", "دیجی‌استایل", "digistyle", "digi style"), setOf("DIGISTYLE")),
            MerchantDefinition("مدیسه", setOf("مدیسه", "modiseh"), setOf("MODISEH")),
        )
    }
}
