package com.example.smsdiscountorganizer.feature.details

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.text.BidiFormatter
import com.example.smsdiscountorganizer.core.format.PersianNumberFormatter
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
    val sourceText by viewModel.sourceText.collectAsStateWithLifecycle()
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
                MerchantCorrectionCard(
                    merchantName = item.effectiveMerchantName,
                    correction = item.merchantCorrection,
                    onSave = viewModel::saveMerchantCorrection,
                )
                item.campaignBrand?.let { brand ->
                    DetailCard("برند کمپین") { Text(brand, style = MaterialTheme.typography.titleLarge) }
                }
                DetailCard("میزان تخفیف") {
                    item.percentageDiscounts?.forEach { Text("${PersianNumberFormatter.formatPercent(it)} تخفیف درصدی") }
                    item.fixedDiscounts?.forEach { Text("${PersianNumberFormatter.formatNumber(it.amount)} ${it.currency.label()} تخفیف") }
                    if (item.percentageDiscounts.isNullOrEmpty() && item.fixedDiscounts.isNullOrEmpty()) {
                        Text(item.discountValue?.let(PersianNumberFormatter::formatNumericText)
                            ?: if (item.freeShipping == true) "ارسال رایگان" else "مقدار مشخص نشده")
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
                            Text(BidiFormatter.getInstance(true).unicodeWrap(code), style = MaterialTheme.typography.titleLarge)
                            Button(onClick = {
                                clipboard.setText(AnnotatedString(code))
                                scope.launch { snackbarHostState.showSnackbar("کد کپی شد") }
                            }) { Text("کپی کد") }
                        }
                    }
                }
                DetailCard("شرایط استفاده") {
                    item.minimumPurchase?.let { Text("حداقل خرید: ${PersianNumberFormatter.formatNumber(it.amount)} ${it.currency.label()}") }
                    val categories = item.applicableCategories.ifEmpty {
                        item.productCategory?.let { category ->
                            com.example.smsdiscountorganizer.domain.offer.OfferCategory.fromLegacy(category)?.let(::listOf).orEmpty()
                        }.orEmpty()
                    }
                    if (categories.isNotEmpty()) Text("دسته‌بندی: ${categories.joinToString("، ") { it.label }}")
                    if (item.freeShipping != true && item.minimumPurchase == null && categories.isEmpty()) {
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
                Text("اطمینان استخراج: ${PersianNumberFormatter.formatPercent((item.extractionConfidence * 100).toInt())}")
                    Text("زمان دریافت پیام: ${formatTimestamp(item.sourceDateMillis, zoneId)}")
                }
                OfferSourceTextCard(sourceText)
            }
        }
    }
}

@Composable
private fun MerchantCorrectionCard(
    merchantName: String?,
    correction: String?,
    onSave: (String?) -> Unit,
) {
    var merchantInput by remember(correction) { mutableStateOf(correction.orEmpty()) }
    DetailCard("فروشگاه") {
        Text(merchantName ?: "فروشگاه نامشخص", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = merchantInput,
            onValueChange = { merchantInput = it.take(50) },
            label = { Text("اصلاح نام فروشگاه") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(merchantInput) }) { Text("ذخیره اصلاح") }
            if (correction != null) {
                TextButton(onClick = {
                    merchantInput = ""
                    onSave(null)
                }) { Text("حذف اصلاح") }
            }
        }
    }
}

@Composable
internal fun OfferSourceTextCard(state: OfferSourceTextState) {
    DetailCard("متن پیام اصلی") {
        when (state) {
            OfferSourceTextState.Loading -> Text("در حال دریافت متن پیام از صندوق ورودی…")
            OfferSourceTextState.Unavailable -> Text(
                "متن پیام در دسترس نیست. دسترسی پیامک لازم است و پیام باید هنوز در صندوق ورودی باشد."
            )
            is OfferSourceTextState.Available -> Text(
                state.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
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
