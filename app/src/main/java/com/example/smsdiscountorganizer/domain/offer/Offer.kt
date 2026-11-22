package com.example.smsdiscountorganizer.domain.offer

/** Persistable offer metadata. It deliberately has no SMS address or message body field. */
data class Offer(
    val sourceSmsId: Long,
    val title: String?,
    val merchantName: String?,
    val discountCode: String?,
    val discountValue: String?,
    val percentageDiscounts: List<Double>?,
    val fixedDiscounts: List<MonetaryAmount>?,
    val freeShipping: Boolean?,
    val minimumPurchase: MonetaryAmount?,
    val productCategory: ProductCategory?,
    val extractionConfidence: Double,
    val validUntilMillis: Long?,
    val sourceDateMillis: Long,
    val extractedAtMillis: Long,
)

data class OfferMetadata(
    val merchantName: String?,
    val offerTitle: String?,
    val percentageDiscounts: List<Double>?,
    val fixedDiscounts: List<MonetaryAmount>?,
    val freeShipping: Boolean?,
    val couponCode: String?,
    val minimumPurchase: MonetaryAmount?,
    val productCategory: ProductCategory?,
    val confidence: Double,
    val validUntilMillis: Long? = null,
)

enum class MoneyCurrency {
    Toman,
    Rial,
}

data class MonetaryAmount(
    val amount: String,
    val currency: MoneyCurrency,
)

enum class ProductCategory {
    Apparel,
    Grocery,
    Electronics,
    Beauty,
    Food,
    Home,
    Travel,
    Entertainment,
}
