package com.example.smsdiscountorganizer.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsdiscountorganizer.core.components.OfferCard
import com.example.smsdiscountorganizer.core.components.formatTimestamp
import com.example.smsdiscountorganizer.core.format.PersianNumberFormatter
import com.example.smsdiscountorganizer.core.permission.SmsPermissionScanCard
import java.time.Clock
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.example.smsdiscountorganizer.domain.scan.SmsDateRangePreset

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    viewModel: DashboardViewModel,
    clock: Clock,
    zoneId: ZoneId,
    onOpenOffers: () -> Unit,
    onOpenOffer: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = clock.millis()
    val listState = rememberLazyListState()
    var showCustomRangePicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .padding(horizontal = 20.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(top = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("پیشنهادهای شما", style = MaterialTheme.typography.headlineLarge)
                    Text("تخفیف‌های مهم را یک‌جا و همیشه در دسترس نگه دارید.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("فعال", state.activeCount, Modifier.weight(1f))
                    SummaryCard("پایان امروز", state.expiringTodayCount, Modifier.weight(1f))
                    SummaryCard("منقضی", state.expiredCount, Modifier.weight(1f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("بازه پیام‌ها", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmsDateRangePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = state.dateRangeSelection.preset == preset,
                                onClick = {
                                    if (preset == SmsDateRangePreset.CUSTOM) showCustomRangePicker = true
                                    else viewModel.selectDateRange(preset)
                                },
                                label = { Text(preset.label()) },
                            )
                        }
                    }
                    if (state.dateRangeSelection.preset == SmsDateRangePreset.CUSTOM) {
                        val start = state.dateRangeSelection.customStartDate
                        val end = state.dateRangeSelection.customEndDate
                        Text(if (start != null && end != null) "از $start تا $end" else "یک بازهٔ دلخواه انتخاب کنید")
                    }
                }
            }
            item {
                SmsPermissionScanCard(
                    isScanning = state.isScanning,
                    onScan = { viewModel.scanNow() },
                    onCancel = viewModel::cancelScan,
                    onFullRescan = { viewModel.scanNow(fullRescan = true) },
                )
            }
            if (state.progress.isScanning) {
                item {
                    Text(
                        PersianNumberFormatter.formatNumericText("${state.progress.processedMessages} پیام بررسی شد؛ ${state.progress.offersFound} پیشنهاد پیدا شد؛ ${state.progress.phase.label()}"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.progress.malformedMessages > 0) {
                item {
                    Text(
                        PersianNumberFormatter.formatNumericText("${state.progress.malformedMessages} پیام نامعتبر نادیده گرفته شد."),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.message?.let { message -> item { Text(PersianNumberFormatter.formatNumericText(message), color = MaterialTheme.colorScheme.primary) } }
            state.readError?.let { error -> item { Text(PersianNumberFormatter.formatNumericText(error), color = MaterialTheme.colorScheme.error) } }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("آخرین بررسی", style = MaterialTheme.typography.titleMedium)
                        Text(state.lastScanAtMillis?.let { formatTimestamp(it, zoneId) } ?: "هنوز بررسی انجام نشده است")
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("پیشنهادهای پایان امروز", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onOpenOffers) { Text("همه پیشنهادها") }
                }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.todayOffers.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("پیشنهاد فوری ندارید", style = MaterialTheme.typography.titleMedium)
                            Text("پیشنهادهایی که مهلتشان امروز تمام می‌شود اینجا نمایش داده می‌شوند.")
                            Button(onClick = onOpenOffers) { Text("دیدن پیشنهادها") }
                        }
                    }
                }
            } else {
                items(state.todayOffers, key = { it.sourceSmsId }) { offer ->
                    OfferCard(offer, now, zoneId, onClick = { onOpenOffer(offer.sourceSmsId) })
                }
            }
            item { Box(Modifier.padding(bottom = 20.dp)) }
        }
    }

    if (showCustomRangePicker) {
        val pickerState = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.dateRangeSelection.customStartDate?.toEpochMilliUtc(),
            initialSelectedEndDateMillis = state.dateRangeSelection.customEndDate?.toEpochMilliUtc(),
        )
        AlertDialog(
            onDismissRequest = { showCustomRangePicker = false },
            title = { Text("بازه دلخواه") },
            text = {
                DateRangePicker(
                    state = pickerState,
                    modifier = Modifier.heightIn(max = 560.dp),
                    title = null,
                    showModeToggle = false,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis?.toLocalDateUtc()
                    val end = pickerState.selectedEndDateMillis?.toLocalDateUtc()
                    if (start != null && end != null) viewModel.selectCustomDateRange(start, end)
                    showCustomRangePicker = false
                }, enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null) {
                    Text("انتخاب")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangePicker = false }) { Text("لغو") }
            },
        )
    }
}

private fun SmsDateRangePreset.label(): String = when (this) {
    SmsDateRangePreset.LAST_7_DAYS -> "۷ روز"
    SmsDateRangePreset.LAST_30_DAYS -> "۳۰ روز"
    SmsDateRangePreset.LAST_2_CALENDAR_MONTHS -> "۲ ماه (پیش‌فرض)"
    SmsDateRangePreset.LAST_3_CALENDAR_MONTHS -> "۳ ماه"
    SmsDateRangePreset.CUSTOM -> "دلخواه"
    SmsDateRangePreset.ALL_MESSAGES -> "همه پیام‌ها"
}

private fun com.example.smsdiscountorganizer.domain.scan.ScanPhase.label(): String = when (this) {
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.IDLE -> "آماده"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.READING -> "خواندن صندوق"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.PROFILING -> "تحلیل فرستنده‌ها"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.PROCESSING -> "تحلیل پیام‌ها"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.SAVING -> "ذخیره محلی"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.COMPLETED -> "تمام شد"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.FAILED -> "ناموفق"
    com.example.smsdiscountorganizer.domain.scan.ScanPhase.CANCELLED -> "لغو شد"
}

private fun LocalDate.toEpochMilliUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun SummaryCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(PersianNumberFormatter.formatNumber(count), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
