package com.example.smsdiscountorganizer.data.repository

import androidx.room.withTransaction
import com.example.smsdiscountorganizer.data.local.AppDatabase
import com.example.smsdiscountorganizer.data.local.OfferEntity
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomOfferRepository @Inject constructor(
    private val database: AppDatabase,
) : OfferRepository {
    override fun observeOffers(): Flow<List<Offer>> =
        database.offerDao().observeAll().map { offers -> offers.map(OfferEntity::toDomain) }

    override suspend fun upsertOffers(offers: List<Offer>) {
        database.withTransaction {
            val dao = database.offerDao()
            dao.upsertAll(offers.map { offer ->
                val entity = offer.toEntity()
                val previousCorrection = dao.getBySourceSmsId(offer.sourceSmsId)?.merchantCorrection
                entity.copy(merchantCorrection = entity.merchantCorrection ?: previousCorrection)
            })
        }
    }

    override suspend fun updateMerchantCorrection(sourceSmsId: Long, merchantName: String?) {
        val correction = merchantName?.trim()?.takeIf(String::isNotEmpty)
        require(correction == null || correction.length <= 50) { "Merchant corrections are limited to 50 characters" }
        database.offerDao().setMerchantCorrection(sourceSmsId, correction)
    }

    override suspend fun clearAllLocalData() {
        database.withTransaction {
            database.offerDao().deleteAll()
            database.scanStateDao().clear()
            database.senderMerchantProfileDao().clear()
            database.processedSmsDao().clear()
            database.appPreferenceDao().clearAll()
        }
    }
}

internal fun OfferEntity.toDomain(): Offer = Offer(
    sourceSmsId = sourceSmsId,
    title = offerTitle,
    merchantName = merchantName,
    discountCode = discountCode,
    discountValue = discountValue,
    percentageDiscounts = percentageDiscounts,
    fixedDiscounts = fixedDiscounts,
    freeShipping = freeShipping,
    minimumPurchase = minimumPurchase,
    productCategory = productCategory,
    extractionConfidence = extractionConfidence,
    validUntilMillis = validUntilMillis,
    sourceDateMillis = sourceDateMillis,
    extractedAtMillis = extractedAtMillis,
    applicableCategories = applicableCategories,
    merchantCorrection = merchantCorrection,
    campaignBrand = campaignBrand,
)

internal fun Offer.toEntity(): OfferEntity = OfferEntity(
    sourceSmsId = sourceSmsId,
    offerTitle = title,
    merchantName = merchantName,
    discountCode = discountCode,
    discountValue = discountValue,
    percentageDiscounts = percentageDiscounts,
    fixedDiscounts = fixedDiscounts,
    freeShipping = freeShipping,
    minimumPurchase = minimumPurchase,
    productCategory = productCategory,
    extractionConfidence = extractionConfidence,
    validUntilMillis = validUntilMillis,
    sourceDateMillis = sourceDateMillis,
    extractedAtMillis = extractedAtMillis,
    applicableCategories = applicableCategories,
    merchantCorrection = merchantCorrection,
    campaignBrand = campaignBrand,
)
