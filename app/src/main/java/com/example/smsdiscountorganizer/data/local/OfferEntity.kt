package com.example.smsdiscountorganizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Only extracted offer metadata is persisted; raw SMS fields are intentionally absent. */
@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey val sourceSmsId: Long,
    val offerTitle: String,
    val merchantName: String?,
    val discountCode: String?,
    val discountValue: String?,
    val validUntilMillis: Long?,
    val sourceDateMillis: Long,
    val extractedAtMillis: Long,
)
