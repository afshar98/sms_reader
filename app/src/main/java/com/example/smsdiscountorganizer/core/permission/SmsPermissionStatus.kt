package com.example.smsdiscountorganizer.core.permission

enum class SmsPermissionStatus {
    Unknown,
    Granted,
    NotRequested,
    RationaleRequired,
    DeniedOrRestricted,
}

object SmsPermissionStatusResolver {
    fun resolve(
        isGranted: Boolean,
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): SmsPermissionStatus = when {
        isGranted -> SmsPermissionStatus.Granted
        shouldShowRationale -> SmsPermissionStatus.RationaleRequired
        hasRequestedBefore -> SmsPermissionStatus.DeniedOrRestricted
        else -> SmsPermissionStatus.NotRequested
    }
}
