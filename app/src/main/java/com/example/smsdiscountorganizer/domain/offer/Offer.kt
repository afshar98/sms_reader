package com.example.smsdiscountorganizer.domain.offer

/** Persistable offer metadata. It deliberately has no SMS address or message body field. */
data class Offer(
    val sourceSmsId: Long,
    val title: String,
    val merchantName: String?,
    val discountCode: String?,
    val discountValue: String?,
    val validUntilMillis: Long?,
    val sourceDateMillis: Long,
    val extractedAtMillis: Long,
)

data class OfferMetadata(
    val title: String,
    val merchantName: String?,
    val discountCode: String?,
    val discountValue: String?,
    val validUntilMillis: Long?,
)
