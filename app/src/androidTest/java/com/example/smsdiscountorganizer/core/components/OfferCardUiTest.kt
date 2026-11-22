package com.example.smsdiscountorganizer.core.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
