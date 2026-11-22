package com.example.smsdiscountorganizer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.ProductCategory

/** Only extracted offer metadata is persisted; raw SMS fields are intentionally absent. */
@Entity(
    tableName = "offers",
    indices = [Index(value = ["sourceDateMillis", "sourceSmsId"], name = "index_offers_sourceDateMillis_sourceSmsId")],
)
data class OfferEntity(
    @PrimaryKey val sourceSmsId: Long,
    val offerTitle: String?,
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
