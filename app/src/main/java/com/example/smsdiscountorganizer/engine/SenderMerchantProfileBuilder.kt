package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.merchant.SenderMerchantProfile
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.extraction.MerchantExtractor
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.security.MessageDigest
import java.util.Locale

/** Streaming, body-free aggregate of explicit merchant evidence from Inbox messages. */
class SenderMerchantProfileBuilder(
    private val merchantExtractor: MerchantExtractor = MerchantExtractor(),
) {
    private val evidence = linkedMapOf<String, MutableMap<String, Int>>()
    private val supportingMessageCounts = linkedMapOf<String, Int>()
    private var lastSeenSmsId: Long? = null

    /** Seeds an incremental rebuild from the persisted body-free aggregate. */
    fun seed(profiles: List<SenderMerchantProfile>) {
        require(evidence.isEmpty() && supportingMessageCounts.isEmpty() && lastSeenSmsId == null) {
            "Profiles can only seed an empty builder"
        }
        profiles.forEach { profile ->
            if (profile.merchantSupport.isNotEmpty()) {
                evidence[profile.senderFingerprint] = profile.merchantSupport.toSortedMap().toMutableMap()
                supportingMessageCounts[profile.senderFingerprint] = profile.totalSupportingMessages
            }
        }
    }

    fun add(message: SmsMessage) {
        if (lastSeenSmsId == message.id) return
        lastSeenSmsId = message.id
        val senderFingerprint = SenderIdNormalizer.fingerprint(message.address) ?: return
        val body = message.body?.takeIf(String::isNotBlank) ?: return
        val merchantNames = merchantExtractor.extractEvidenceNames(body)
        if (merchantNames.isEmpty()) return

        val senderEvidence = evidence.getOrPut(senderFingerprint) { linkedMapOf() }
        supportingMessageCounts[senderFingerprint] = (supportingMessageCounts[senderFingerprint] ?: 0) + 1
        // A merchant repeated many times in one SMS is still one independent message.
        merchantNames.forEach { merchant -> senderEvidence[merchant] = (senderEvidence[merchant] ?: 0) + 1 }
    }

    fun build(): List<SenderMerchantProfile> = evidence.map { (sender, merchants) ->
        val sorted = merchants.toSortedMap()
        val total = supportingMessageCounts[sender] ?: 0
        val conflict = sorted.size > 1
        val sharedGateway = sorted.values.count { it >= MIN_SHARED_GATEWAY_SUPPORT } > 1
        val winner = sorted.entries.singleOrNull()
        val winningSupport = winner?.value ?: 0
        val confidence = if (winner == null) 0.0 else minOf(
            MAX_CONFIDENCE,
            winningSupport.toDouble() / (winningSupport + 1.0),
        )
        SenderMerchantProfile(
            senderFingerprint = sender,
            merchantSupport = sorted,
            totalSupportingMessages = total,
            isConflicted = conflict,
            isSharedGateway = sharedGateway,
            confidence = confidence,
            resolvedMerchantName = winner?.key?.takeIf {
                !conflict && !sharedGateway && confidence >= MIN_RESOLUTION_CONFIDENCE
            },
        )
    }.sortedBy(SenderMerchantProfile::senderFingerprint)

    fun fingerprint(): String = profileFingerprint(build())

    companion object {
        const val MIN_RESOLUTION_CONFIDENCE = 0.75
        private const val MIN_SHARED_GATEWAY_SUPPORT = 2
        private const val MAX_CONFIDENCE = 0.97

        fun profileFingerprint(profiles: List<SenderMerchantProfile>): String {
            val canonical = buildString {
                profiles.sortedBy { it.senderFingerprint }.forEach { profile ->
                    append(profile.senderFingerprint.length).append(':').append(profile.senderFingerprint)
                    profile.merchantSupport.toSortedMap().forEach { (name, count) ->
                        append(name.length).append(':').append(name)
                        append(count).append(';')
                    }
                    append('\n')
                }
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

/** Stable normalization used only to group sender IDs; only its SHA-256 fingerprint is stored. */
object SenderIdNormalizer {
    private val textNormalizer = PersianTextNormalizer()

    fun normalize(senderId: String?): String? {
        val raw = senderId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalized = textNormalizer.normalize(raw)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it == '+' }
            .let { value -> if (value.startsWith("00")) "+${value.drop(2)}" else value }
        return normalized.takeIf(String::isNotEmpty)
    }

    fun fingerprint(senderId: String?): String? = normalize(senderId)?.let { normalized ->
        MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
