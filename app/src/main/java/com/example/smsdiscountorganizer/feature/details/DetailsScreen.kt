package com.example.smsdiscountorganizer.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsdiscountorganizer.core.components.formatTimestamp
import com.example.smsdiscountorganizer.feature.offers.OfferStatus
import com.example.smsdiscountorganizer.feature.offers.statusAt
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    clock: Clock,
    zoneId: ZoneId,
    onBack: () -> Unit,
) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (offer == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) { Text("بازگشت به پیشنهادها") }
                Text("این پیشنهاد در دستگاه پیدا نشد.", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            val item = requireNotNull(offer)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextButton(onClick = onBack) { Text("بازگشت به پیشنهادها") }
                Text(item.title ?: "جزئیات پیشنهاد", style = MaterialTheme.typography.headlineMedium)
                Text(item.merchantName ?: "فروشگاه نامشخص", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                DetailCard("میزان تخفیف") {
                    item.percentageDiscounts?.forEach { Text("${it}% تخفیف درصدی") }
                    item.fixedDiscounts?.forEach { Text("${it.amount} ${it.currency.label()} تخفیف") }
                    if (item.percentageDiscounts.isNullOrEmpty() && item.fixedDiscounts.isNullOrEmpty()) {
                        Text(item.discountValue ?: if (item.freeShipping == true) "ارسال رایگان" else "مقدار مشخص نشده")
                    }
                    if (item.freeShipping == true) Text("ارسال رایگان")
                }
                item.discountCode?.let { code ->
                    DetailCard("کد تخفیف") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(code, style = MaterialTheme.typography.titleLarge)
                            Button(onClick = {
                                clipboard.setText(AnnotatedString(code))
                                scope.launch { snackbarHostState.showSnackbar("کد کپی شد") }
                            }) { Text("کپی کد") }
                        }
                    }
                }
                DetailCard("شرایط استفاده") {
                    item.minimumPurchase?.let { Text("حداقل خرید: ${it.amount} ${it.currency.label()}") }
                    item.productCategory?.let { Text("دسته‌بندی: ${it.label()}") }
                    if (item.freeShipping != true && item.minimumPurchase == null && item.productCategory == null) {
                        Text("شرط دیگری ثبت نشده است.")
                    }
                }
                DetailCard("مهلت") {
                    Text(item.validUntilMillis?.let { formatTimestamp(it, zoneId) } ?: "مهلت مشخص نشده")
                    item.validUntilMillis?.let {
                        Text(when (item.statusAt(clock.millis(), zoneId)) {
                            OfferStatus.ACTIVE -> "وضعیت: فعال"
                            OfferStatus.EXPIRING_TODAY -> "وضعیت: پایان امروز"
                            OfferStatus.EXPIRED -> "وضعیت: منقضی‌شده"
                            OfferStatus.UNKNOWN -> "وضعیت: نامشخص"
                        })
                    }
                }
                DetailCard("اطلاعات استخراج") {
                    Text("اطمینان استخراج: ${(item.extractionConfidence * 100).toInt()}٪")
                    Text("زمان دریافت پیام: ${formatTimestamp(item.sourceDateMillis, zoneId)}")
                }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun com.example.smsdiscountorganizer.domain.offer.MoneyCurrency.label(): String = when (this) {
    com.example.smsdiscountorganizer.domain.offer.MoneyCurrency.Toman -> "تومان"
    com.example.smsdiscountorganizer.domain.offer.MoneyCurrency.Rial -> "ریال"
}

private fun com.example.smsdiscountorganizer.domain.offer.ProductCategory.label(): String = when (this) {
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Apparel -> "پوشاک"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Grocery -> "خواربار"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Electronics -> "کالای دیجیتال"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Beauty -> "زیبایی و سلامت"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Food -> "غذا و نوشیدنی"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Home -> "خانه"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Travel -> "سفر"
    com.example.smsdiscountorganizer.domain.offer.ProductCategory.Entertainment -> "سرگرمی"
}
