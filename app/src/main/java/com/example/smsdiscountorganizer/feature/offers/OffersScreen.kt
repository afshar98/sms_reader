package com.example.smsdiscountorganizer.feature.offers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsdiscountorganizer.core.components.OfferCard
import java.time.Clock
import java.time.ZoneId

@Composable
fun OffersScreen(
    viewModel: OffersViewModel,
    clock: Clock,
    zoneId: ZoneId,
    onOpenDetails: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = clock.millis()
    val listState = rememberLazyListState()
    var merchantMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پیشنهادها", style = MaterialTheme.typography.headlineLarge)
                Text("تخفیف‌ها را جست‌وجو و بر اساس مهلت مرتب کنید.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("جست‌وجو") },
                placeholder = { Text("نام فروشگاه، کد یا تخفیف") },
                singleLine = true,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { merchantMenuExpanded = true }) {
                        Text(state.selectedMerchant ?: "همه فروشگاه‌ها")
                    }
                    DropdownMenu(
                        expanded = merchantMenuExpanded,
                        onDismissRequest = { merchantMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("همه فروشگاه‌ها") },
                            onClick = { viewModel.setMerchant(null); merchantMenuExpanded = false },
                        )
                        state.merchants.forEach { merchant ->
                            DropdownMenuItem(
                                text = { Text(merchant) },
                                onClick = { viewModel.setMerchant(merchant); merchantMenuExpanded = false },
                            )
                        }
                    }
                }
                Button(onClick = viewModel::toggleExpirationSort) {
                    Text(if (state.expirationAscending) "نزدیک‌ترین مهلت" else "دورترین مهلت")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OfferStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { viewModel.setStatusFilter(filter) },
                        label = { Text(filter.label()) },
                    )
                }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        when {
            state.isLoading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.error != null -> item { EmptyOffersCard("بارگذاری ناموفق", state.error ?: "") }
            state.offers.isEmpty() -> item { EmptyOffersCard("هنوز پیشنهادی ذخیره نشده", "با اسکن صندوق پیامک، پیشنهادهای تخفیف اینجا جمع می‌شوند.") }
            state.visibleOffers.isEmpty() -> item { EmptyOffersCard("نتیجه‌ای پیدا نشد", "عبارت جست‌وجو یا فیلترها را تغییر دهید.") }
            else -> items(state.visibleOffers, key = { it.sourceSmsId }) { offer ->
                OfferCard(offer, now, zoneId, onClick = { onOpenDetails(offer.sourceSmsId) })
            }
        }
        item { Box(Modifier.padding(bottom = 20.dp)) }
    }
}

@Composable
private fun EmptyOffersCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun OfferStatusFilter.label(): String = when (this) {
    OfferStatusFilter.ALL -> "همه"
    OfferStatusFilter.ACTIVE -> "فعال"
    OfferStatusFilter.TODAY -> "پایان امروز"
    OfferStatusFilter.EXPIRED -> "منقضی"
    OfferStatusFilter.UNKNOWN -> "بدون تاریخ"
}
