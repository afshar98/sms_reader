package com.example.smsdiscountorganizer.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.smsdiscountorganizer.core.theme.OrganizerTheme
import org.junit.Rule
import org.junit.Test

class OfferSourceTextCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysTheUnmodifiedOriginalMessage() {
        val originalText = "فروشگاه مهر ۲۰٪ تخفیف\nکد تخفیف Mehr20"
        composeRule.setContent {
            OrganizerTheme {
                OfferSourceTextCard(OfferSourceTextState.Available(originalText))
            }
        }

        composeRule.onNodeWithText(originalText).assertIsDisplayed()
    }
}
