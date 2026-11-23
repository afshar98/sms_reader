package com.example.smsdiscountorganizer.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.feature.offers.OfferStatus
import com.example.smsdiscountorganizer.feature.offers.statusAt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun OfferCard(
    offer: Offer,
    nowMillis: Long,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    offer.effectiveMerchantName ?: "فروشگاه نامشخص",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(statusLabel(offer.statusAt(nowMillis, zoneId)), style = MaterialTheme.typography.labelMedium)
            }
            offer.campaignBrand?.let { Text("برند کمپین: $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                offer.title ?: offer.discountValue ?: if (offer.discountCode != null) "کد تخفیف" else "پیشنهاد تخفیف",
                style = MaterialTheme.typography.titleLarge,
            )
            offer.discountValue?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            offer.discountCode?.let { Text("کد تخفیف: $it", style = MaterialTheme.typography.bodyMedium) }
            offer.fixedDiscounts?.joinToString("، ") { "${it.amount} ${it.currency.toPersian()}" }
                ?.takeIf(String::isNotBlank)
                ?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            offer.validUntilMillis?.let {
                Text("مهلت استفاده: ${formatTimestamp(it, zoneId)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun statusLabel(status: OfferStatus): String = when (status) {
    OfferStatus.ACTIVE -> "فعال"
    OfferStatus.EXPIRING_TODAY -> "پایان امروز"
    OfferStatus.EXPIRED -> "منقضی‌شده"
    OfferStatus.UNKNOWN -> "بدون مهلت مشخص"
}

fun formatTimestamp(timestampMillis: Long, zoneId: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("fa-IR"))
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(timestampMillis))

private fun com.example.smsdiscountorganizer.domain.offer.MoneyCurrency.toPersian(): String = when (this) {
    com.example.smsdiscountorganizer.domain.offer.MoneyCurrency.Toman -> "تومان"
    com.example.smsdiscountorganizer.domain.offer.MoneyCurrency.Rial -> "ریال"
}
