package com.example.smsdiscountorganizer.engine

import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import com.example.smsdiscountorganizer.engine.extraction.MerchantExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderMerchantProfileBuilderTest {
    @Test
    fun equivalentSenderIdsShareProfileWithoutPersistingAddress() {
        val builder = SenderMerchantProfileBuilder()
        builder.add(message(1, "+98 912-000-0000", "فروشگاه دیجی‌کالا؛ تخفیف ویژه"))
        builder.add(message(2, "۰۰۹۸ ۹۱۲ ۰۰۰ ۰۰۰۰", "کد تخفیف دیجی کالا"))
        builder.add(message(3, "+989120000000", "پیشنهاد ویژه دیجی‌کالا"))

        val profile = builder.build().single()

        assertEquals(3, profile.merchantSupport.getValue("دیجی‌کالا"))
        assertEquals("دیجی‌کالا", profile.resolvedMerchantName)
        assertTrue(profile.confidence >= SenderMerchantProfileBuilder.MIN_RESOLUTION_CONFIDENCE)
        assertNotEquals("+989120000000", profile.senderFingerprint)
        assertFalse(profile.isConflicted)
    }

    @Test
    fun repeatedMentionsInOneSmsCountAsOneIndependentSupport() {
        val builder = SenderMerchantProfileBuilder()
        builder.add(message(1, "SHOP", "دیجی‌کالا دیجی کالا Digikala"))
        builder.add(message(2, "SHOP", "دیجی‌کالا"))

        val profile = builder.build().single()

        assertEquals(2, profile.merchantSupport.getValue("دیجی‌کالا"))
        assertNull(profile.resolvedMerchantName)
    }

    @Test
    fun duplicateInboxRowDoesNotIncreaseIndependentSupport() {
        val builder = SenderMerchantProfileBuilder()
        val duplicate = message(1, "SHOP", "فروشگاه دیجی‌کالا")
        builder.add(duplicate)
        builder.add(duplicate)

        assertEquals(1, builder.build().single().merchantSupport.getValue("دیجی‌کالا"))
    }

    @Test
    fun seededProfilesAndNewMessagesProduceSameProfileAsFullBuild() {
        val initialMessages = listOf(
            message(1, "SHOP", "فروشگاه دیجی‌کالا"),
            message(2, "SHOP", "کد تخفیف دیجی کالا"),
        )
        val fullBuilder = SenderMerchantProfileBuilder().apply { initialMessages.forEach(::add) }
        val incrementalBuilder = SenderMerchantProfileBuilder().apply {
            seed(fullBuilder.build())
            add(message(3, "SHOP", "پیشنهاد ویژه دیجی‌کالا"))
        }
        val expected = SenderMerchantProfileBuilder().apply {
            (initialMessages + message(3, "SHOP", "پیشنهاد ویژه دیجی‌کالا")).forEach(::add)
        }

        assertEquals(expected.build(), incrementalBuilder.build())
        assertEquals(expected.fingerprint(), incrementalBuilder.fingerprint())
    }

    @Test
    fun conflictingNamesRemainUnknownAndSharedGatewaysAreMarked() {
        val builder = SenderMerchantProfileBuilder()
        repeat(3) { builder.add(message(it.toLong(), "GATEWAY-1", "فروشگاه دیجی‌کالا")) }
        repeat(3) { builder.add(message((it + 3).toLong(), "GATEWAY-1", "فروشگاه اسنپ‌فود")) }

        val profile = builder.build().single()

        assertTrue(profile.isConflicted)
        assertTrue(profile.isSharedGateway)
        assertNull(profile.resolvedMerchantName)
    }

    @Test
    fun weakAndGenericMessagesDoNotCreateMerchantProfiles() {
        val builder = SenderMerchantProfileBuilder()
        builder.add(message(1, "INFO", "تبریک! پیشنهاد ویژه امروز"))
        builder.add(message(2, null, "فروشگاه دیجی‌کالا تخفیف دارد"))
        builder.add(message(3, "INFO", "فروشگاه خانومی"))

        assertEquals(1, builder.build().size)
        assertNull(builder.build().single().resolvedMerchantName)
    }

    @Test
    fun benchmarkFixtureMeasuresAtLeastTwoHundredSyntheticMessages() {
        val builder = SenderMerchantProfileBuilder()
        val expected = mutableListOf<Pair<String, String>>()
        val merchants = listOf("دیجی‌کالا", "اسنپ‌فود", "تپسی", "ترب", "بانی‌مد")
        repeat(200) { index ->
            val sender = "BRAND-${index % merchants.size}"
            val expectedName = merchants[index % merchants.size]
            val includesMerchant = index % 10 < 8
            val body = when {
                !includesMerchant -> "پیام خدماتی و پیشنهاد ویژه بدون نام فروشگاه"
                index % 4 == 0 -> "نام فروشگاه: $expectedName؛ کد تخفیف ۲۰٪"
                index % 4 == 1 -> "${expectedName}؛ جشنواره فروش و خرید با تخفیف"
                index % 4 == 2 -> "${expectedName} کد خرید و ارسال رایگان"
                else -> "پیشنهاد ویژه فروشگاه $expectedName"
            }
            builder.add(message(index.toLong(), sender, body))
            expected += SenderIdNormalizer.fingerprint(sender)!! to expectedName
        }

        val measured = builder.build().associateBy { it.senderFingerprint }
        val exactMatches = expected.count { (sender, merchant) ->
            measured[sender]?.resolvedMerchantName == merchant
        }

        assertEquals(200, expected.size)
        assertEquals(200, exactMatches)
    }

    private fun message(id: Long, sender: String?, body: String) =
        SmsMessage(id, sender, body, dateMillis = id)
}
