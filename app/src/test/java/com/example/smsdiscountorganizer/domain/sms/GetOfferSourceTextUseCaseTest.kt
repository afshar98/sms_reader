package com.example.smsdiscountorganizer.domain.sms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetOfferSourceTextUseCaseTest {
    @Test
    fun returnsUnmodifiedSourceTextForDiscountOffer() = runBlocking {
        val original = "فروشگاه مهر ۲۰٪ تخفیف\nکد تخفیف Mehr20"
        val useCase = GetOfferSourceTextUseCase(SourceMessageDataSource(SmsMessage(42, null, original, 123L)))

        assertEquals(original, useCase(42L))
    }

    @Test
    fun doesNotExposeOtpMessageEvenIfItContainsDiscountWords() = runBlocking {
        val otp = "رمز پویا ۱۲۳۴ است؛ کد تخفیف در پیام دیگری آمده بود"
        val useCase = GetOfferSourceTextUseCase(SourceMessageDataSource(SmsMessage(42, null, otp, 123L)))

        assertNull(useCase(42L))
    }

    @Test
    fun doesNotExposePaymentNotificationText() = runBlocking {
        val payment = "پرداخت مبلغ ۵۰۰۰۰۰ تومان با موفقیت انجام شد"
        val useCase = GetOfferSourceTextUseCase(SourceMessageDataSource(SmsMessage(42, null, payment, 123L)))

        assertNull(useCase(42L))
    }

    @Test
    fun doesNotExposeSourceTextWithoutSmsPermission() = runBlocking {
        val useCase = GetOfferSourceTextUseCase(SourceMessageDataSource(failure = SecurityException()))

        assertNull(useCase(42L))
    }

    private class SourceMessageDataSource(
        private val message: SmsMessage? = null,
        private val failure: Exception? = null,
    ) : SmsDataSource {
        override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = emptyFlow()

        override suspend fun readMessageById(id: Long): SmsMessage? {
            failure?.let { throw it }
            return message?.takeIf { it.id == id }
        }
    }
}
