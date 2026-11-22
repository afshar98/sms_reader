package com.example.smsdiscountorganizer.core.permission

enum class SmsPermissionStatus {
    Unknown,
    Granted,
    NotRequested,
    RationaleRequired,
    PermanentlyDenied,
    InstallerRestricted,
}

object SmsPermissionStatusResolver {
    fun resolve(
        isGranted: Boolean,
        isInstallerAllowlisted: Boolean,
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): SmsPermissionStatus = when {
        isGranted -> SmsPermissionStatus.Granted
        !isInstallerAllowlisted -> SmsPermissionStatus.InstallerRestricted
        shouldShowRationale -> SmsPermissionStatus.RationaleRequired
        hasRequestedBefore -> SmsPermissionStatus.PermanentlyDenied
        else -> SmsPermissionStatus.NotRequested
    }
}
