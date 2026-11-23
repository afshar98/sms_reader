package com.example.smsdiscountorganizer.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
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
                SmsPermissionScanCard(
                    isScanning = state.isScanning,
                    onScan = { viewModel.scanNow() },
                    onFullRescan = { viewModel.scanNow(fullRescan = true) },
                )
            }
            if (state.progress.isScanning) {
                item {
                    Text(
                        PersianNumberFormatter.formatNumericText("${state.progress.processedMessages} پیام بررسی شد؛ ${state.progress.offersFound} پیشنهاد پیدا شد."),
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
}

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
