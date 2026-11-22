package com.example.smsdiscountorganizer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsdiscountorganizer.core.permission.SmsPermissionScanCard
import com.example.smsdiscountorganizer.core.permission.SmsPermissionStatus
import com.example.smsdiscountorganizer.domain.settings.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenSmsDiagnostics: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionStatus by rememberSaveable { mutableStateOf(SmsPermissionStatus.Unknown) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("تنظیمات", style = MaterialTheme.typography.headlineLarge)
            Text("کنترل حریم خصوصی و ظاهر برنامه", style = MaterialTheme.typography.bodyLarge)
        }
        SmsPermissionScanCard(
            isScanning = state.isScanning,
            onScan = viewModel::scanNow,
            onPermissionStatus = { permissionStatus = it },
        )
        Text("وضعیت دسترسی: ${permissionStatus.label()}", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenSmsDiagnostics) { Text("جزئیات و عیب‌یابی صندوق پیامک") }
        if (state.progress.isScanning) {
            Text(
                "${state.progress.processedMessages} پیام بررسی شد؛ ${state.progress.offersFound} پیشنهاد پیدا شد.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.scanMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("پوسته برنامه", style = MaterialTheme.typography.titleLarge)
                Text("ظاهر روشن، تیره یا هماهنگ با دستگاه را انتخاب کنید.")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(mode.label()) },
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حریم خصوصی شما", style = MaterialTheme.typography.titleLarge)
                Text("پیامک‌ها فقط از صندوق ورودی و فقط برای پردازش روی همین دستگاه خوانده می‌شوند.")
                Text("برنامه هیچ پیامی ارسال، ویرایش یا حذف نمی‌کند. متن کامل پیام‌ها ذخیره یا ثبت نمی‌شود.")
                Text("هیچ اتصال شبکه، سرویس ابری، تبلیغات یا رهگیری در برنامه وجود ندارد.")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("داده‌های محلی", style = MaterialTheme.typography.titleLarge)
                Text("پاک‌کردن، پیشنهادهای ذخیره‌شده و زمان آخرین اسکن را از این دستگاه حذف می‌کند.")
                Button(onClick = { showClearConfirmation = true }) { Text("پاک‌کردن همه داده‌ها") }
            }
        }
        state.clearMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Column(Modifier.padding(bottom = 20.dp)) { Text("نسخه ۱٫۰", style = MaterialTheme.typography.labelMedium) }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("پاک‌کردن داده‌های برنامه؟") },
            text = { Text("پیشنهادها، زمان اسکن و انتخاب پوسته از این دستگاه حذف می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    viewModel.clearLocalData()
                }) { Text("پاک‌کردن") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("انصراف") } },
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "هماهنگ با دستگاه"
    ThemeMode.LIGHT -> "روشن"
    ThemeMode.DARK -> "تیره"
}

private fun SmsPermissionStatus.label(): String = when (this) {
    SmsPermissionStatus.Unknown -> "در حال بررسی"
    SmsPermissionStatus.Granted -> "فعال"
    SmsPermissionStatus.NotRequested -> "درخواست نشده"
    SmsPermissionStatus.RationaleRequired -> "رد شده؛ امکان درخواست دوباره هست"
    SmsPermissionStatus.PermanentlyDenied -> "رد شده؛ از تنظیمات فعال کنید"
    SmsPermissionStatus.InstallerRestricted -> "محدودشده توسط نصب‌کننده Android"
}
