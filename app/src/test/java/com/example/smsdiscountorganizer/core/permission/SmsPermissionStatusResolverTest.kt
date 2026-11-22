package com.example.smsdiscountorganizer.core.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsPermissionStatusResolverTest {
    @Test
    fun grantTakesPrecedenceOverOtherPermissionFlags() {
        assertEquals(
            SmsPermissionStatus.Granted,
            SmsPermissionStatusResolver.resolve(
                isGranted = true,
                isInstallerAllowlisted = false,
                hasRequestedBefore = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun reportsHardRestrictionWhenInstallerHasNotAllowlistedPermission() {
        assertEquals(
            SmsPermissionStatus.InstallerRestricted,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                isInstallerAllowlisted = false,
                hasRequestedBefore = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun reportsRationaleAfterOrdinaryDenial() {
        assertEquals(
            SmsPermissionStatus.RationaleRequired,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                isInstallerAllowlisted = true,
                hasRequestedBefore = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun reportsSettingsRecoveryAfterPermanentDenial() {
        assertEquals(
            SmsPermissionStatus.PermanentlyDenied,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                isInstallerAllowlisted = true,
                hasRequestedBefore = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun newInstallStartsInNotRequestedState() {
        assertEquals(
            SmsPermissionStatus.NotRequested,
            SmsPermissionStatusResolver.resolve(
                isGranted = false,
                isInstallerAllowlisted = true,
                hasRequestedBefore = false,
                shouldShowRationale = false,
            ),
        )
    }
}
