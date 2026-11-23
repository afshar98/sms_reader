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
    val applicableCategories: List<OfferCategory> = productCategory?.let(::legacyCategoryList).orEmpty(),
    val merchantCorrection: String? = null,
) {
    val effectiveMerchantName: String? get() = merchantCorrection ?: merchantName
}

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
    val applicableCategories: List<OfferCategory> = productCategory?.let(::legacyCategoryList).orEmpty(),
)

private fun legacyCategoryList(category: ProductCategory): List<OfferCategory> =
    listOf(OfferCategory.fromStableId(category.name.lowercase()) ?: OfferCategory.UNKNOWN)

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

/** Stable IDs form a hierarchy; display names and enum ordinals are not persisted. */
enum class OfferCategory(
    val stableId: String,
    val parentStableId: String? = null,
    val label: String,
) {
    GENERAL("general", label = "عمومی"),
    SHOPPING("shopping", "general", "خرید"),
    Apparel("apparel", "shopping", "پوشاک"),
    Electronics("electronics", "shopping", "کالای دیجیتال"),
    Beauty("beauty", "shopping", "زیبایی و سلامت"),
    Home("home", "shopping", "خانه"),
    FOOD_AND_DRINK("food_and_drink", "general", "خوراک و نوشیدنی"),
    Grocery("grocery", "food_and_drink", "خواربار"),
    Food("food", "food_and_drink", "غذا و نوشیدنی"),
    Travel("travel", "general", "سفر"),
    Entertainment("entertainment", "general", "سرگرمی"),
    UNKNOWN("unknown", label = "نامشخص");

    companion object {
        fun fromStableId(id: String): OfferCategory? = entries.firstOrNull { it.stableId == id }

        fun fromLegacy(category: ProductCategory): OfferCategory? =
            fromStableId(category.name.lowercase())
    }
}
