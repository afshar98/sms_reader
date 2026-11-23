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
)

/** On-device alias registry. Unknown senders and generic gateway IDs are never brands by default. */
class MerchantRegistry(
    definitions: List<MerchantDefinition> = defaultDefinitions(),
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val definitions = definitions.toList()

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
            (definition.aliases + definition.canonicalName).any { alias ->
                containsWholeAlias(normalizedText, normalize(alias))
            }
        }
        if (bodyMatches.size == 1) return MerchantMatch(bodyMatches.single().canonicalName, MerchantEvidence.BODY_ALIAS)
        if (bodyMatches.size > 1) return null

        val sender = senderAddress.cleanMerchant()?.let(::normalize) ?: return null
        if (sender in GENERIC_SENDERS) return null
        val senderMatches = definitions.filter { definition ->
            definition.senderAliases.any { normalize(it) == sender }
        }
        return senderMatches.singleOrNull()?.let {
            MerchantMatch(it.canonicalName, MerchantEvidence.SENDER_ID)
        }
    }

    fun canonicalize(value: String): String {
        val normalized = normalize(value)
        return definitions.firstOrNull { definition ->
            (definition.aliases + definition.canonicalName).any { normalize(it) == normalized }
        }?.canonicalName ?: value.trim()
    }

    /** Returns every locally registered brand explicitly named in the supplied message. */
    fun bodyMentions(text: String): Set<String> {
        val normalizedText = normalize(text)
        return definitions.filter { definition ->
            (definition.aliases + definition.canonicalName).any { alias ->
                containsWholeAlias(normalizedText, normalize(alias))
            }
        }.mapTo(linkedSetOf(), MerchantDefinition::canonicalName)
    }

    private fun normalize(value: String): String =
        normalizer.normalize(value).lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()

    private fun containsWholeAlias(text: String, alias: String): Boolean {
        if (alias.isBlank()) return false
        val pattern = Regex("(?<![\\p{L}\\p{N}\\u200C])${Regex.escape(alias)}(?![\\p{L}\\p{N}\\u200C])")
        return pattern.containsMatchIn(text)
    }

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
            MerchantDefinition("اسنپ‌فود", setOf("اسنپ فود", "اسنپ‌فود", "snappfood", "snapp food"), setOf("SNAPPFOOD")),
            MerchantDefinition("اسنپ‌مارکت", setOf("اسنپ مارکت", "اسنپ‌مارکت", "snappmarket", "snapp market"), setOf("SNAPPMARKET")),
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
