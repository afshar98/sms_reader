package com.example.smsdiscountorganizer.core.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun SmsPermissionScanCard(
    isScanning: Boolean,
    onScan: () -> Unit,
    onCancel: () -> Unit = {},
    onFullRescan: (() -> Unit)? = null,
    onPermissionStatus: (SmsPermissionStatus) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val manager = remember(context) { SmsPermissionManager(context.applicationContext) }
    var status by remember { mutableStateOf(SmsPermissionStatus.Unknown) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        status = activity?.let(manager::status) ?: SmsPermissionStatus.Unknown
        onPermissionStatus(status)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }
    LaunchedEffect(activity) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("دسترسی صندوق پیامک", style = MaterialTheme.typography.titleMedium)
            Text(permissionDescription(status), style = MaterialTheme.typography.bodyMedium)
            when (status) {
                SmsPermissionStatus.Granted -> {
                    Button(onClick = onScan, enabled = !isScanning) {
                        Text(if (isScanning) "در حال بررسی صندوق…" else "بررسی بازه انتخابی")
                    }
                    if (isScanning) {
                        OutlinedButton(onClick = onCancel) { Text("لغو بررسی") }
                    }
                    onFullRescan?.let { rescan ->
                        OutlinedButton(onClick = rescan, enabled = !isScanning) {
                            Text("بازاسکن کامل بازه")
                        }
                    }
                }
                SmsPermissionStatus.NotRequested, SmsPermissionStatus.RationaleRequired -> Button(
                    onClick = { showExplanation = true },
                    enabled = activity != null,
                ) { Text("توضیح و درخواست دسترسی") }
                SmsPermissionStatus.DeniedOrRestricted -> Button(
                    onClick = { activity?.let(manager::openAppSettings) },
                    enabled = activity != null,
                ) { Text("بازکردن تنظیمات برنامه") }
                SmsPermissionStatus.Unknown -> Text("در حال بررسی وضعیت دسترسی…")
            }
        }
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("چرا دسترسی پیامک لازم است؟") },
            text = {
                Text("برای دسته‌بندی تخفیف‌ها، برنامه پیام‌های صندوق ورودی را فقط روی همین دستگاه می‌خواند. پیام‌ها ارسال، ویرایش یا حذف نمی‌شوند و متن کامل آن‌ها ذخیره یا ثبت نمی‌شود.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExplanation = false
                    manager.markRequestAttempted()
                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                }) { Text("ادامه") }
            },
            dismissButton = { TextButton(onClick = { showExplanation = false }) { Text("فعلاً نه") } },
        )
    }
}

private fun permissionDescription(status: SmsPermissionStatus): String = when (status) {
    SmsPermissionStatus.Unknown -> "وضعیت دسترسی در حال بررسی است."
    SmsPermissionStatus.Granted -> "دسترسی فقط‌خواندنی برای صندوق ورودی فعال است."
    SmsPermissionStatus.NotRequested -> "برای شروع اسکن محلی، دسترسی فقط‌خواندنی لازم است."
    SmsPermissionStatus.RationaleRequired -> "دسترسی قبلاً رد شده است؛ توضیح را ببینید و در صورت تمایل دوباره درخواست کنید."
    SmsPermissionStatus.DeniedOrRestricted -> "دسترسی داده نشده است. تنظیمات برنامه را بررسی کنید؛ Android ممکن است این مجوز را برای این نصب محدود کرده باشد. برنامه این محدودیت را دور نمی‌زند."
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
