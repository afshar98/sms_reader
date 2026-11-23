package com.example.smsdiscountorganizer.core.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.smsdiscountorganizer.core.theme.OrganizerTheme
import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.Offer
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OfferCardUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offerCardShowsPersianDiscountAndOpensDetails() {
        val clicked = AtomicBoolean(false)
        val offer = Offer(
            sourceSmsId = 17,
            title = "تخفیف کفش",
            merchantName = "فروشگاه مهر",
            discountCode = "Mehr20",
            discountValue = "۲۰٪",
            percentageDiscounts = listOf(20.0),
            fixedDiscounts = null,
            freeShipping = null,
            minimumPurchase = MonetaryAmount("500000", MoneyCurrency.Toman),
            productCategory = null,
            extractionConfidence = 0.95,
            validUntilMillis = 1_800_000_000_000,
            sourceDateMillis = 1_700_000_000_000,
            extractedAtMillis = 1_700_000_000_000,
        )
        composeRule.setContent {
            OrganizerTheme {
                OfferCard(offer, 1_700_000_000_000, ZoneId.of("Asia/Tehran"), onClick = { clicked.set(true) })
            }
        }

        composeRule.onNodeWithText("فروشگاه مهر").assertIsDisplayed()
        composeRule.onNodeWithText("۲۰٪").assertIsDisplayed()
        composeRule.onNode(hasClickAction()).performClick()
        assertTrue(clicked.get())
    }

    @Test
    fun couponOnlyOfferShowsCouponLabelWithoutInventingAPercentage() {
        val offer = Offer(
            sourceSmsId = 18,
            title = null,
            merchantName = "تپسی‌مارکت",
            discountCode = "xyzp",
            discountValue = null,
            percentageDiscounts = null,
            fixedDiscounts = null,
            freeShipping = null,
            minimumPurchase = null,
            productCategory = null,
            extractionConfidence = 0.9,
            validUntilMillis = null,
            sourceDateMillis = 1_700_000_000_000,
            extractedAtMillis = 1_700_000_000_000,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                OrganizerTheme {
                    OfferCard(offer, 1_700_000_000_000, ZoneId.of("Asia/Tehran"), onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("کد تخفیف").assertIsDisplayed()
        composeRule.onNodeWithText("کد تخفیف:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("xyzp", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("بدون مهلت مشخص").assertIsDisplayed()
    }

    @Test
    fun offerCardFormatsDiscountAndGroupedAmountsWithPersianDigitsInRtlUi() {
        val offer = Offer(
            sourceSmsId = 19,
            title = "تخفیف 80%",
            merchantName = "فروشگاه مهر",
            discountCode = "SAVE20",
            discountValue = "80%",
            percentageDiscounts = listOf(80.0),
            fixedDiscounts = listOf(MonetaryAmount("150000", MoneyCurrency.Toman)),
            freeShipping = null,
            minimumPurchase = null,
            productCategory = null,
            extractionConfidence = 0.95,
            validUntilMillis = null,
            sourceDateMillis = 1_700_000_000_000,
            extractedAtMillis = 1_700_000_000_000,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                OrganizerTheme {
                    OfferCard(offer, 1_700_000_000_000, ZoneId.of("Asia/Tehran"), onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("تخفیف ۸۰٪").assertIsDisplayed()
        composeRule.onNodeWithText("۸۰٪").assertIsDisplayed()
        composeRule.onNodeWithText("۱۵۰٬۰۰۰ تومان").assertIsDisplayed()
        composeRule.onNodeWithText("SAVE20", substring = true).assertIsDisplayed()
    }
}
