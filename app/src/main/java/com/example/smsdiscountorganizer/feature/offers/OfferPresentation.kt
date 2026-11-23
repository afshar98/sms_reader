package com.example.smsdiscountorganizer.feature.offers

import com.example.smsdiscountorganizer.domain.offer.Offer
import java.time.Instant
import java.time.ZoneId

enum class OfferStatus { ACTIVE, EXPIRING_TODAY, EXPIRED, UNKNOWN }

enum class OfferStatusFilter(val status: OfferStatus?) {
    ALL(null), ACTIVE(OfferStatus.ACTIVE), TODAY(OfferStatus.EXPIRING_TODAY),
    EXPIRED(OfferStatus.EXPIRED), UNKNOWN(OfferStatus.UNKNOWN),
}

fun Offer.statusAt(nowMillis: Long, zoneId: ZoneId): OfferStatus {
    val expiry = validUntilMillis ?: return OfferStatus.UNKNOWN
    if (expiry < nowMillis) return OfferStatus.EXPIRED
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return if (Instant.ofEpochMilli(expiry).atZone(zoneId).toLocalDate() == today) {
        OfferStatus.EXPIRING_TODAY
    } else {
        OfferStatus.ACTIVE
    }
}

fun filterOffers(
    offers: List<Offer>,
    query: String,
    status: OfferStatusFilter,
    merchant: String?,
    nowMillis: Long,
    zoneId: ZoneId,
    expirationAscending: Boolean = true,
): List<Offer> = offers
    .asSequence()
    .filter { offer ->
        val normalizedQuery = query.trim()
        normalizedQuery.isEmpty() || listOfNotNull(
            offer.title,
            offer.effectiveMerchantName,
            offer.discountCode,
            offer.discountValue,
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }
    .filter { offer -> merchant == null || offer.effectiveMerchantName == merchant }
    .filter { offer -> status.status == null || offer.statusAt(nowMillis, zoneId) == status.status }
    .sortedWith { first, second ->
        val firstExpiry = first.validUntilMillis
        val secondExpiry = second.validUntilMillis
        when {
            firstExpiry == null && secondExpiry == null -> second.sourceDateMillis.compareTo(first.sourceDateMillis)
            firstExpiry == null -> 1
            secondExpiry == null -> -1
            expirationAscending -> firstExpiry.compareTo(secondExpiry)
            else -> secondExpiry.compareTo(firstExpiry)
        }
    }
    .toList()
