package com.example.smsdiscountorganizer.core.permission

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smsdiscountorganizer.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SmsPermissionStartupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchWithReadSmsDeniedShowsPermissionCard() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            targetContext.checkSelfPermission(Manifest.permission.READ_SMS) !=
                PackageManager.PERMISSION_GRANTED,
        )

        composeRule.onNodeWithText("دسترسی صندوق پیامک").assertIsDisplayed()
    }
}
