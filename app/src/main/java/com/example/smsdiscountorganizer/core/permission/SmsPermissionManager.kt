package com.example.smsdiscountorganizer.core.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

class SmsPermissionManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun status(activity: Activity): SmsPermissionStatus {
        val isGranted = appContext.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

        return SmsPermissionStatusResolver.resolve(
            isGranted = isGranted,
            hasRequestedBefore = preferences.getBoolean(KEY_HAS_REQUESTED, false),
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS),
        )
    }

    fun hasReadPermission(): Boolean = appContext.checkSelfPermission(Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

    fun markRequestAttempted() {
        preferences.edit().putBoolean(KEY_HAS_REQUESTED, true).apply()
    }

    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
        }
        activity.startActivity(intent)
    }

    private companion object {
        const val PREFERENCES_NAME = "sms_permission_state"
        const val KEY_HAS_REQUESTED = "read_sms_request_attempted"
    }
}
