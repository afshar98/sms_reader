package com.example.smsdiscountorganizer.core.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsPermissionStatusResolverTest {
    @Test
    fun unrequestedPermissionIsShownAsNotRequested() {
        assertEquals(
            SmsPermissionStatus.NotRequested,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                hasRequestedBefore = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun grantTakesPrecedenceOverOtherPermissionFlags() {
        assertEquals(
            SmsPermissionStatus.Granted,
            SmsPermissionStatusResolver.resolve(
                isGranted = true,
                hasRequestedBefore = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun reportsRationaleAfterOrdinaryDenial() {
        assertEquals(
            SmsPermissionStatus.RationaleRequired,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                hasRequestedBefore = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun reportsSettingsRecoveryWhenNoRationaleCanBeShown() {
        assertEquals(
            SmsPermissionStatus.DeniedOrRestricted,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                hasRequestedBefore = true,
                shouldShowRationale = false,
            ),
        )
    }
}
